package encry.analyzer.event.processor.consumer

import cats.effect.{ ConcurrentEffect, ContextShift, Sync, Timer }
import encry.analyzer.event.event
import cats.syntax.flatMap._
import encry.analyzer.event.event.{
  explorerEventKafkaSerializer,
  ExplorerCoreLogEvent,
  ExplorerEvent,
  ExplorerObserverLogEvent,
  NewBlockReceived
}
import encry.analyzer.settings.AnalyzerSettings
import fs2.Stream
import fs2.concurrent.Queue
import fs2.kafka._
import fs2.kafka.{ AutoOffsetReset, ConsumerSettings, Deserializer }
import io.chrisdavenport.log4cats.Logger

trait KafkaConsumer[F[_]] {
  def runConsumer: Stream[F, Unit]
}

object KafkaConsumer {
  def apply[F[_]: ConcurrentEffect: ContextShift: Timer: Logger](
    signalsForStatisticRecord: Queue[F, ExplorerEvent],
    signalsForGeneratorEvent: Queue[F, ExplorerEvent],
    signalsForNetworkLogProcessor: Queue[F, ExplorerEvent],
    signalsForCoreLogProcessor: Queue[F, ExplorerEvent],
    AS: AnalyzerSettings
  ): KafkaConsumer[F] =
    new KafkaConsumer[F] {
      override def runConsumer: Stream[F, Unit] = kafkaConsumer

      private val consumerSettings: ConsumerSettings[F, String, Option[event.ExplorerEvent]] =
        ConsumerSettings(
          keyDeserializer = Deserializer[F, String],
          valueDeserializer = explorerEventKafkaSerializer.createDeserializer[F]
        ).withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(AS.consumerSettings.serverUrl)
          .withGroupId(AS.consumerSettings.groupId)

      private def kafkaConsumer: Stream[F, Unit] =
        consumerStream(consumerSettings)
          .evalTap(_.subscribeTo("ObserverLogEvent", "CoreLogEvent", "ChainEvent"))
          .flatMap(_.stream)
          .mapAsync(AS.consumerSettings.concurrentSize) { r =>
            r.record.value match {
              case Some(value: NewBlockReceived) =>
                signalsForGeneratorEvent.enqueue1(value) >> signalsForStatisticRecord.enqueue1(value) >>
                  Logger[F].info(s"New event $value was sent to statistic and generator")
              case Some(value: ExplorerObserverLogEvent) =>
                signalsForNetworkLogProcessor.enqueue1(value) >>
                  Logger[F].info(s"Sent event ${value.kafkaKey} to core logs processor")
              case Some(value: ExplorerCoreLogEvent) =>
                signalsForCoreLogProcessor.enqueue1(value) >>
                  Logger[F].info(s"Sent event ${value.kafkaKey} to core logs processor")
              case Some(value) =>
                signalsForStatisticRecord.enqueue1(value) >>
                  Logger[F].info(s"New event $value was sent to statistic.")
              case None => Logger[F].info(s"Inconsistent event received.")
            }
          }
    }
}

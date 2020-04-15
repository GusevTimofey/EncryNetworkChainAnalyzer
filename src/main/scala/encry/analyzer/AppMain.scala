package encry.analyzer

import cats.effect.ExitCode
import cats.syntax.functor._
import encry.analyzer.event.event.ExplorerEvent
import encry.analyzer.event.processor.consumer.KafkaConsumer
import encry.analyzer.settings.settingsReader
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import monix.eval.{ Task, TaskApp }

object AppMain extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    (for {
      implicit0(logger: SelfAwareStructuredLogger[Task]) <- Slf4jLogger.create[Task]
      as                                                 <- settingsReader.read[Task]
      _                                                  <- logger.info(s"Settings read successfully.")
      signalsForStatisticRecord                          <- Queue.bounded[Task, ExplorerEvent](200)
      signalsForGeneratorEvent                           <- Queue.bounded[Task, ExplorerEvent](200)
      signalsForNetworkLogProcessor                      <- Queue.bounded[Task, ExplorerEvent](200)
      signalsForCoreLogProcessor                         <- Queue.bounded[Task, ExplorerEvent](200)
      ks = KafkaConsumer(
        signalsForStatisticRecord,
        signalsForGeneratorEvent,
        signalsForNetworkLogProcessor,
        signalsForCoreLogProcessor,
        as
      )
      _ <- logger.info(s"ks service created")
      _ <- ks.runConsumer.compile.drain
    } yield ()).as(ExitCode.Success)
}

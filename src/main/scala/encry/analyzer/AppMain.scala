package encry.analyzer

import java.util.concurrent.{ Executors, ThreadFactory }

import cats.effect.{ ExitCode, Resource }
import cats.syntax.functor._
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.paulgoldbaum.influxdbclient.{ Database, InfluxDB }
import encry.analyzer.event.event.ExplorerEvent
import encry.analyzer.event.processor.consumer.KafkaConsumer
import encry.analyzer.influx.statistic.InfluxAPI
import encry.analyzer.influx.statistic.algebra.LiftFuture
import encry.analyzer.settings.{ settingsReader, AnalyzerSettings }
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.SelfAwareStructuredLogger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import monix.eval.{ Task, TaskApp }
import monix.execution.Callback

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor, Future }

object AppMain extends TaskApp {
  override def run(args: List[String]): Task[ExitCode] =
    resources.use {
      case (as, influx, ec) =>
        (for {
          implicit0(logger: SelfAwareStructuredLogger[Task]) <- Slf4jLogger.create[Task]
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
          implicit0(liftFuture: LiftFuture[Task]) = new LiftFuture[Task] {
            override def liftFuture[T](v: Future[T]): Task[T] =
              Task.async { cb: Callback[Throwable, T] => v.onComplete(r => cb(r))(ec) }
          }
          is = InfluxAPI(as, signalsForStatisticRecord, influx)
          _  <- (ks.runConsumer concurrently is.run).compile.drain
        } yield ()).as(ExitCode.Success)
    }

  private def resources: Resource[Task, (AnalyzerSettings, Database, ExecutionContextExecutor)] =
    for {
      as <- Resource.liftF(settingsReader.read[Task])
      tf: ThreadFactory = new ThreadFactoryBuilder()
        .setNameFormat("influx-db-thread-pool-%d")
        .setDaemon(false)
        .setPriority(Thread.NORM_PRIORITY)
        .build()
      ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(
        Executors.newFixedThreadPool(3, tf)
      )
      influx: InfluxDB <- Resource.make(
                           Task.delay(
                             InfluxDB.connect(
                               as.influxSettings.url,
                               as.influxSettings.port,
                               as.influxSettings.login,
                               as.influxSettings.password
                             )(ec)
                           )
                         )(m => Task.eval(m.close()))
    } yield (as, influx.selectDatabase(as.influxSettings.dbName), ec)
}

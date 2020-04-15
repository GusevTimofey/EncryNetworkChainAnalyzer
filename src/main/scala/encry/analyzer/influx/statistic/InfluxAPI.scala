package encry.analyzer.influx.statistic

import cats.effect.Async
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import com.paulgoldbaum.influxdbclient.Parameter.{ Consistency, Precision }
import com.paulgoldbaum.influxdbclient._
import encry.analyzer.event.event._
import encry.analyzer.settings.AnalyzerSettings
import fs2.Stream
import fs2.concurrent.Queue
import io.chrisdavenport.log4cats.Logger

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }

trait InfluxAPI[F[_]] {
  def run: Stream[F, Unit]
}

object InfluxAPI {
  def apply[F[_]: Logger: Async](
    AS: AnalyzerSettings,
    signalsForStatisticRecord: Queue[F, ExplorerEvent],
    influx: Database,
    influxContext: ExecutionContext
  ): InfluxAPI[F] =
    new InfluxAPI[F] {
      override def run: Stream[F, Unit] = processInputEvents

      private def processInputEvents: Stream[F, Unit] =
        signalsForStatisticRecord.dequeue.evalMap { receivedEvent =>
          val point: Point = receivedEvent match {
            case newEvent: UnavailableNode =>
              Point("network").addTag("unavailable", "node").addField("url", newEvent.url)
            case newEvent: NewBlockReceived =>
              Point("network").addTag("block", "received").addField("id", newEvent.id)
            case newEvent: RollbackOccurred =>
              Point("network")
                .addTag("rollback", "occurred")
                .addField("id", newEvent.branchPoint)
                .addField("height", newEvent.height)
            case newEvent: NewNode => Point("network").addTag("new", "node").addField("url", newEvent.url)
            case newEvent: ForkOccurred =>
              Point("network")
                .addTag("fork", "occurred")
                .addField("id", newEvent.id)
                .addField("height", newEvent.height)
            case m => Point("influxService").addTag("inconsistent", "behaviour").addField("name", m.kafkaKey)
          }
          Async[F].async { cb: (Either[Throwable, Unit] => Unit) =>
            influx
              .write(point, precision = Precision.SECONDS, consistency = Consistency.ALL, retentionPolicy = "autogen")
              .onComplete { r =>
                cb(r match {
                  case Failure(exception) => Left(exception)
                  case Success(_)         => Right(())
                })
              }(influxContext)
          }.handleErrorWith { err =>
            Logger[F].info(s"Error ${err.getMessage} has occurred while processing insertion into db.")
          } >> Logger[F].info(s"New point $point was inserted!")
        }
    }
}

package encry.analyzer.influx.statistic.algebra

import simulacrum.typeclass

import scala.concurrent.Future

@typeclass trait LiftFuture[F[_]] {
  def liftFuture[T](v: Future[T]): F[T]
}

object LiftFuture {
  object syntax {
    implicit class LiftFutureOps[T](val f: Future[T]) extends AnyVal {
      def liftFuture[F[_]: LiftFuture]: F[T] = LiftFuture[F].liftFuture(f)
    }
  }
}

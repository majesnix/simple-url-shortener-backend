package com.majesnix.sus

import cats.data.{Kleisli, OptionT}
import cats.effect.{Clock, IO, Ref}
import org.http4s.{HttpApp, Method, Request, Response, Status}
import org.http4s.headers.`Retry-After`
import org.typelevel.ci._

import scala.concurrent.duration._

/** Fixed-window, per-client limit on link creation (POST). Resolving links is
  * never limited.
  *
  * The client is identified by the TCP peer address, or — when the service runs
  * behind `forwardedForHops` trusted reverse proxies — by the X-Forwarded-For
  * entry that the outermost trusted proxy appended. Entries further left are
  * client-controlled and therefore ignored.
  */
final class RateLimiter private (
    windows: Ref[IO, Map[String, RateLimiter.Window]],
    maxRequests: Int,
    window: FiniteDuration,
    forwardedForHops: Int
) {
  import RateLimiter._

  private[sus] def clientKey(req: Request[IO]): String = {
    val forwarded =
      if (forwardedForHops <= 0) None
      else
        req.headers
          .get(ci"X-Forwarded-For")
          .map(_.toList.flatMap(_.value.split(',').map(_.trim)))
          .map(_.filter(_.nonEmpty))
          .flatMap(entries => entries.lift(entries.length - forwardedForHops))
    forwarded
      .orElse(req.remoteAddr.map(_.toString))
      .getOrElse("unknown")
  }

  /** Returns None if the request is allowed, else how long until it would be.
    */
  private[sus] def acquire(key: String): IO[Option[FiniteDuration]] =
    Clock[IO].monotonic.flatMap { now =>
      windows.modify { current =>
        val pruned =
          if (current.size < MaxTrackedClients) current
          else current.filter { case (_, w) => now - w.start < window }
        pruned.get(key) match {
          case Some(w) if now - w.start < window =>
            if (w.count < maxRequests)
              (pruned.updated(key, w.copy(count = w.count + 1)), None)
            else (pruned, Some(window - (now - w.start)))
          case _ => (pruned.updated(key, Window(now, 1)), None)
        }
      }
    }

  def apply(app: HttpApp[IO]): HttpApp[IO] =
    if (maxRequests <= 0) app
    else
      Kleisli { req =>
        if (req.method != Method.POST) app.run(req)
        else
          OptionT(acquire(clientKey(req)))
            .semiflatMap(retryIn => IO.pure(tooManyRequests(retryIn)))
            .getOrElseF(app.run(req))
      }

  private def tooManyRequests(retryIn: FiniteDuration): Response[IO] =
    Response[IO](Status.TooManyRequests)
      .withEntity("Too many requests")
      .putHeaders(`Retry-After`.unsafeFromLong(math.max(1L, retryIn.toSeconds)))
}

object RateLimiter {
  private val MaxTrackedClients = 10000

  final case class Window(start: FiniteDuration, count: Int)

  def create(
      maxRequests: Int,
      window: FiniteDuration,
      forwardedForHops: Int
  ): IO[RateLimiter] =
    Ref
      .of[IO, Map[String, Window]](Map.empty)
      .map(new RateLimiter(_, maxRequests, window, forwardedForHops))
}

package lila.fishnet
package http

import cats.effect.IO
import io.circe.{ Codec, Decoder, Encoder }

import HealthCheck.*

trait HealthCheck:
  def status: IO[AppStatus]

object HealthCheck:

  def apply(): HealthCheck = new HealthCheck:
    def status: IO[AppStatus] = IO.pure(AppStatus(true))

  case class AppStatus(status: Boolean) derives Codec.AsObject

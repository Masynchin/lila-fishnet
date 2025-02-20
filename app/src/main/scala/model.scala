package lila.fishnet

import cats.syntax.all.*
import chess.format.{ Fen, Uci }
import chess.variant.Variant
import chess.variant.Variant.LilaKey
import io.circe.Decoder.decodeString
import io.circe.Encoder.encodeString
import io.circe.{ Codec, Decoder, Encoder }

opaque type ClientKey = String
object ClientKey:
  def apply(value: String): ClientKey         = value
  given Encoder[ClientKey]                    = encodeString
  given Decoder[ClientKey]                    = decodeString
  extension (ck: ClientKey) def value: String = ck

opaque type BestMove = String
object BestMove:
  def apply(value: String): BestMove = value
  given Encoder[BestMove]            = encodeString
  given Decoder[BestMove]            = decodeString
  extension (bm: BestMove)
    def value: String    = bm
    def uci: Option[Uci] = Uci(bm)

opaque type WorkId = String
object WorkId:
  def apply(value: String): WorkId         = value
  given Encoder[WorkId]                    = encodeString
  given Decoder[WorkId]                    = decodeString
  extension (bm: WorkId) def value: String = bm

opaque type GameId = String
object GameId:
  def apply(value: String): GameId         = value
  given Encoder[GameId]                    = encodeString
  given Decoder[GameId]                    = decodeString
  extension (bm: GameId) def value: String = bm

object ChessCirceCodecs:
  given Encoder[Fen.Epd] = encodeString.contramap(_.value)
  given Decoder[Fen.Epd] = decodeString.map(Fen.Epd.apply)
  given Encoder[Variant] = encodeString.contramap(_.name)
  given Decoder[Variant] =
    decodeString.emap: s =>
      Variant.byName(s).toRight(s"Invalid variant: $s")

object Fishnet:

  import ChessCirceCodecs.given

  case class Acquire(fishnet: Fishnet) derives Codec.AsObject
  case class Fishnet(version: String, apikey: ClientKey) derives Codec.AsObject
  case class PostMove(fishnet: Fishnet, move: Move) derives Codec.AsObject
  case class Move(bestmove: BestMove)

  case class Work(id: WorkId, level: Int, clock: Option[Lila.Clock], `type`: String = "move")
      derives Encoder.AsObject

  case class WorkResponse(
      work: Work,
      game_id: GameId,
      position: Fen.Epd,
      moves: String,
      variant: Variant
  ) derives Encoder.AsObject

object Lila:

  import ChessCirceCodecs.given

  case class Move(gameId: GameId, moves: String, uci: Uci):
    def sign  = moves.takeRight(20).replace(" ", "")
    def write = s"$gameId $sign ${uci.uci}"

  case class Request(
      id: GameId,
      initialFen: Fen.Epd,
      variant: Variant,
      moves: String,
      level: Int,
      clock: Option[Clock]
  ) derives Codec.AsObject

  case class Clock(wtime: Int, btime: Int, inc: Int) derives Codec.AsObject

  def readMoveReq(msg: String): Option[Request] =
    msg.split(";", 6) match
      case Array(gameId, levelS, clockS, variantS, initialFenS, moves) =>
        levelS.toIntOption.map: level =>
          val variant    = chess.variant.Variant.orDefault(LilaKey(variantS))
          val initialFen = readFen(initialFenS).getOrElse(variant.initialFen)
          val clock      = readClock(clockS)
          Request(
            id = GameId(gameId),
            initialFen = initialFen,
            variant = variant,
            moves = moves,
            level = level,
            clock = clock
          )
      case _ => None

  def readFen(str: String): Option[Fen.Epd] =
    Option.when(str.nonEmpty)(Fen.Epd(str))

  def readClock(s: String): Option[Clock] =
    s.split(' ') match
      case Array(ws, bs, incs) =>
        (ws.toIntOption, bs.toIntOption, incs.toIntOption).mapN(Clock.apply)
      case _ => None

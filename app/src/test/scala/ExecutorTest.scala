package lila.fishnet

import weaver.*
import cats.effect.IO
import cats.effect.kernel.Ref
import java.time.Instant

object ExecutorTest extends SimpleIOSuite:

  val request: Lila.Request = Lila.Request(
    id = GameId("id"),
    initialFen = chess.variant.Standard.initialFen,
    variant = chess.variant.Standard,
    moves = "",
    level = 1,
    clock = None,
  )

  val key = ClientKey("key")

  val validMove   = BestMove("e2e4")
  val invalidMove = BestMove("2e4")

  test("acquire when there is no work should return none"):
    for
      executor <- createExecutor()
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  test("acquire when there is work should return work.some"):
    for
      executor       <- createExecutor()
      _              <- executor.add(request)
      acquiredOption <- executor.acquire(key)
      acquired = acquiredOption.get
    yield assert(acquired.request == request)

  test("after acquire the only work, acquire again should return none"):
    for
      executor <- createExecutor()
      _        <- executor.add(request)
      _        <- executor.acquire(key)
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  test("post move after acquire should send move"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove)
      move     <- ref.get.map(_.head)
    yield assert(move == Lila.Move(request.id, request.moves, chess.format.Uci.Move("e2e4").get))

  test("post move after timeout should not send move"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      _        <- executor.move(acquired.get.id, key, validMove)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after timeout should move can be acruired again"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      _        <- executor.acquire(key)
      _        <- executor.clean(Instant.now.plusSeconds(37))
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, validMove)
      move     <- ref.get.map(_.head)
    yield assert(move == Lila.Move(request.id, request.moves, chess.format.Uci.Move("e2e4").get))

  test("post an invalid move should not send move"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      _        <- executor.move(acquired.get.id, key, invalidMove)
      moves    <- ref.get
    yield assert(moves.isEmpty)

  test("after post an invalid move, acquire again should return work.some"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      acquired <- executor.acquire(key)
      workId = acquired.get.id
      _              <- executor.move(workId, key, invalidMove)
      acquiredOption <- executor.acquire(key)
      acquired = acquiredOption.get
    yield assert(acquired == Work.RequestWithId(workId, request))

  test("should not give up after 2 tries"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      _        <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(2)
      acquired <- executor.acquire(key)
    yield assert(acquired.isDefined)

  test("should give up after 3 tries"):
    for
      ref <- Ref.of[IO, List[Lila.Move]](Nil)
      client = createLilaClient(ref)
      executor <- Executor.instance(client)
      _        <- executor.add(request)
      _        <- (executor.acquire(key).flatMap(x => executor.move(x.get.id, key, invalidMove))).replicateA_(3)
      acquired <- executor.acquire(key)
    yield assert(acquired.isEmpty)

  def createExecutor(): IO[Executor] =
    createLilaClient.flatMap(Executor.instance)

  def createLilaClient: IO[LilaClient] =
    Ref.of[IO, List[Lila.Move]](Nil)
      .map(createLilaClient)

  def createLilaClient(ref: Ref[IO, List[Lila.Move]]): LilaClient =
    new LilaClient:
      def send(move: Lila.Move): IO[Unit] =
        ref.update(_ :+ move)

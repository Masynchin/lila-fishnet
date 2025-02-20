package lila.fishnet

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

trait StateRepository:
  def get: IO[AppState]
  def save(state: AppState): IO[Unit]

object StateRepository:

  def instance(path: Option[String])(using Logger[IO]): StateRepository =
    path.fold(noop)(file(_))

  def noop(using Logger[IO]): StateRepository =
    new StateRepository:
      def get: IO[AppState] =
        Logger[IO].info("There is no configed path, return empty AppState") *> IO(AppState.empty)
      def save(state: AppState): IO[Unit] = Logger[IO].info("There is no configed path, do nothing")

  def file(_path: String)(using Logger[IO]): StateRepository =
    val path = fs2.io.file.Path(_path)
    new StateRepository:
      def get: IO[AppState] =
        Logger[IO].info(s"Reading state from $path") *>
          fs2.io.file
            .Files[IO]
            .readAll(path)
            .through(TasksSerDe.deserialize)
            .compile
            .toList
            .map(AppState.fromTasks)
            .handleErrorWith: e =>
              Logger[IO].error(e)(s"Failed to read state from $path") *> IO.pure(AppState.empty)

      def save(state: AppState): IO[Unit] =
        Logger[IO].info(s"Saving ${state.size} tasks to $path") *>
          fs2.Stream
            .emits(state.tasks)
            .through(TasksSerDe.serialize)
            .through(fs2.text.utf8.encode)
            .through(fs2.io.file.Files[IO].writeAll(path))
            .compile
            .drain
            .handleErrorWith: e =>
              Logger[IO].error(e)(s"Failed to write state to $path")

object TasksSerDe:

  import fs2.data.json.*
  import fs2.data.json.circe.*

  def deserialize: fs2.Pipe[IO, Byte, Work.Task] =
    _.through(fs2.text.utf8.decode)
      .through(tokens[IO, String])
      .through(codec.deserialize[IO, Work.Task])

  def serialize: fs2.Pipe[IO, Work.Task, String] =
    _.through(codec.serialize[IO, Work.Task])
      .through(render.compact)

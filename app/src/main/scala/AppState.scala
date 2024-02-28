package lila.fishnet

import cats.syntax.all.*
import java.time.Instant
import lila.fishnet.Work.Task

opaque type AppState = Map[WorkId, Work.Task]

enum GetTaskResult:
  case NotFound
  case Found(task: Work.Task)
  case AcquiredByOther(task: Work.Task)

object AppState:
  val empty: AppState = Map.empty
  extension (state: AppState)

    inline def isFull(maxSize: Int): Boolean =
      state.sizeIs >= maxSize

    inline def add(task: Task): AppState =
      state + (task.id -> task)

    inline def remove(id: WorkId): AppState =
      state - id

    inline def size: Int = state.size

    inline def count(p: Task => Boolean): Int = state.count(x => p(x._2))

    def tryAcquireTask(key: ClientKey, at: Instant): (AppState, Option[Task]) =
      state.earliestNonAcquiredTask
        .map: newTask =>
          val assignedTask = newTask.assignTo(key, at)
          state.updated(assignedTask.id, assignedTask) -> assignedTask.some
        .getOrElse(state -> none)

    def apply(workId: WorkId, key: ClientKey): GetTaskResult =
      state.get(workId) match
        case None                                 => GetTaskResult.NotFound
        case Some(task) if task.isAcquiredBy(key) => GetTaskResult.Found(task)
        case Some(task)                           => GetTaskResult.AcquiredByOther(task)

    def updateOrGiveUp(candidates: List[Work.Task]): (AppState, List[Work.Task]) =
      candidates.foldLeft[(AppState, List[Work.Task])](state -> Nil) { case ((state, xs), task) =>
        task.clearAssignedKey match
          case None                 => (state - task.id, task :: xs)
          case Some(unAssignedTask) => (state.updated(task.id, unAssignedTask), xs)
      }

    def acquiredBefore(since: Instant): List[Work.Task] =
      state.values.filter(_.acquiredBefore(since)).toList

    def earliestNonAcquiredTask: Option[Work.Task] =
      state.values.filter(_.nonAcquired).minByOption(_.createdAt)

package util

import collection.mutable.{Map => MMap}
import scala.concurrent.{Future, Await}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

// TODO: introduce a read-write lock
abstract class RefreshableCache[ID, T](eagerLoad: Boolean) {
  //  protected val lock: Lock
  protected val cache = {
    val map = MMap[ID, T]()
    if (eagerLoad)
      refresh(map)
    map
  }

  def apply(id: ID) =
    cache.get(id)

  protected def cacheMissGet(id: ID): Option[T] = None

  private def refresh(map : MMap[ID, T]): Unit = this.synchronized {
    map.clear()
    val future =
      for {
        // collect all ids
        ids <- getAllIds

        // create all instances
        idInstances <-
          Future.sequence(
            ids.map(id =>
              createInstance(id).map(instance => (id, instance))
            )
          )
      } yield
        idInstances.toMap

    Await.result(future, 10 minutes)
  }

  def refresh: Unit = this.synchronized {
    refresh(cache)
  }

  protected def getAllIds: Future[Traversable[ID]]

  protected def createInstance(id: ID): Future[T]
}
package util

import collection.mutable.{Map => MMap}
import scala.concurrent.{Future, Await}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.duration._

// TODO: introduce a read-write lock
abstract class RefreshableCache[ID, T] {
  //  protected val lock: Lock
  protected val cache = {
    val map = MMap[ID, T]()
    refresh(map)
    map
  }

  def apply(id: ID) =
    cache.get(id)

  private def refresh(map : MMap[ID, T]): Unit = this.synchronized {
    map.clear()
    val future = getAllIds.map(_.map(id =>
      map.put(id, createInstance(id))
    ))
    Await.result(future,  120000 millis)
  }

  def refresh: Unit = this.synchronized {
    refresh(cache)
  }

  protected def getAllIds: Future[Traversable[ID]]

  protected def createInstance(id: ID): T
}
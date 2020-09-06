package org.ada.server.dataaccess.dataset

import collection.mutable.{Map => MMap}
import scala.concurrent.{Await, Future}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.concurrent.ConcurrentHashMap

import scala.concurrent.duration._

/**
  * Simple cache with hooks for initialization of stored objects via `createInstance(id: ID)` function.
  * The only exposed (public) function is `apply(id: ID)`, which retrieves a cached object or tries to create one if possible.
  * Note that when ids are provided and `eagerInit` is set to true, the corresponding objects are created and cached on startup.
  *
  * @param eagerInit The flag indicating eager initialization
  * @tparam ID The key type
  * @tparam T The cached object type
  */
abstract class SimpleInitializableCache[ID, T](eagerInit: Boolean) {

  private val locks = new ConcurrentHashMap[ID, AnyRef]()

  protected val cache = {
    val map = MMap[ID, T]()
    if (eagerInit)
      initialize(map)
    map
  }

  def apply(id: ID): Option[T] =
    Await.result(applyAsync(id), 10 minutes)

  def applyAsync(id: ID): Future[Option[T]] =
    getItemOrElse(id) {

      // get a lock... if doesn't exist, register one
      val lock =  {
        val lockAux = locks.putIfAbsent(id, new AnyRef())
        // putIfAbsent returns 'null' if there was no associated object (in our case a lock) for a given key
        if (lockAux == null) locks.get(id) else lockAux
      }

      lock.synchronized {
        getItemOrElse(id)(
          createAndCacheInstance(id)
        )
      }
    }

  private def getItemOrElse(
    id: ID)(
    initialize: => Future[Option[T]]
  ): Future[Option[T]] =
    cache.get(id) match {
      case Some(item) => Future(Some(item))
      case None => initialize
    }

  private def createAndCacheInstance(id: ID) =
    createInstance(id).map { instance =>
      if (instance.isDefined)
        cache.put(id, instance.get)
      instance
    }

  protected def cacheMissGet(id: ID): Future[Option[T]] = Future(None)

  private def initialize(map : MMap[ID, T]): Unit = this.synchronized {
    map.clear()
    val future =
      for {
        // collect all ids
        ids <- getAllIds

        // create all instances
        idInstances <- createInstances(ids)
      } yield
        idInstances.map { case (id, instance) =>
          map.put(id, instance)
        }

    Await.result(future, 10 minutes)
  }

  protected def createInstances(
    ids: Traversable[ID]
  ): Future[Traversable[(ID, T)]] =
    Future.sequence(
      ids.map(id =>
        createInstance(id).map(_.map(instance => (id, instance)))
      )
    ).map(_.flatten)

  // implementation hook
  protected def getAllIds: Future[Traversable[ID]]

  // implementation hook
  protected def createInstance(id: ID): Future[Option[T]]
}
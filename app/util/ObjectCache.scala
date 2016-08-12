package util

import java.util.Calendar

import play.api.Logger

/**
  * Trait for caching.
  * Override updateInterval and updateCall.
  * Use getCache to access cached objects.
  * @tparam T Class type to use.
  */
@Deprecated
trait ObjectCache[T] {
  // time of last update
  protected var lastUpdate: Long = 0
  // cached objects
  protected var cached: Traversable[T] = Traversable[T]()
  // update interval in seconds
  protected val updateInterval: Int
  // current time in seconds
  protected def currentTime: Long = (Calendar.getInstance().getTimeInMillis() / 1000)
  // check if update required
  protected def needsUpdate: Boolean = ((currentTime - lastUpdate) > updateInterval)
  // access cache
  // use this instead of accessing elements directly
  def getCache(forceUpdate: Boolean = false): Traversable[T] = {
    if(forceUpdate || needsUpdate){
      val newCache: Traversable[T] = updateCall()
      if(!newCache.isEmpty){
        lastUpdate = currentTime
        cached = newCache
        Logger.info("cache updated")
      }else{
        Logger.warn("cache update failed")
      }
    }
    cached
  }
  // update function; override this!
  val updateCall: (() => Traversable[T])
}

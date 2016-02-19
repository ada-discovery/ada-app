package security

import play.api.mvc.Request
import be.objectify.deadbolt.core.models.Subject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * DeadboltHandler to be used if no Subject present
  *
  */
class MyUserlessDeadboltHandler extends MyDeadboltHandler
{
  // Dummy method. Always returns Future(None)
  override def getSubject[A](request: Request[A]): Future[Option[Subject]] = Future(None)
}
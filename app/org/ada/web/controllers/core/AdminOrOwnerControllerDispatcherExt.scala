package org.ada.web.controllers.core

import be.objectify.deadbolt.scala.DeadboltHandler
import org.ada.web.models.security.DeadboltUser
import org.incal.play.controllers.SecureControllerDispatcher
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AdminOrOwnerControllerDispatcherExt[C] {

  this: SecureControllerDispatcher[C] =>

  override type USER = DeadboltUser

  protected def dispatchIsAdminOrOwnerOrPublicAux(
    objectOwnerIdAndIsPublic: Request[AnyContent] => Future[Option[(BSONObjectID, Boolean)]],
    outputHandler: DeadboltHandler = handlerCache()
  ): DispatchActionTransformation = { cAction =>
    AuthAction { implicit request =>
      val checkOwner = restrictUserCustomAny(
        { (user, request) =>
          for {
            objectOwnerIdIsPublicOption <- objectOwnerIdAndIsPublic(request)
          } yield
            objectOwnerIdIsPublicOption match {
              case Some((createdById, isPublic)) =>
                // is public or the user accessing the data view is the owner => all is ok
                isPublic || user.id.get.equals(createdById)

              // if the owner (and public flag) not specified then non-authorized
              case None => false
            }
        },
        outputHandler
      )

      val actionTransformation = restrictChainAny(Seq(restrictAdminAny(outputHandler = unauthorizedDeadboltHandler), checkOwner))
      val autAction = toAuthenticatedAction(dispatch(cAction))
      actionTransformation(autAction)(request)
    }
  }

  protected def dispatchIsAdminOrOwnerAux(
    objectOwnerId: Request[AnyContent] => Future[Option[BSONObjectID]],
    outputHandler: DeadboltHandler = handlerCache()
  ) = dispatchIsAdminOrOwnerOrPublicAux(
    request => objectOwnerId(request).map(_.map((_, false))),
    outputHandler
  )

  protected def dispatchIsAdmin: DispatchActionTransformation = { cAction =>
    val autAction = toAuthenticatedAction(dispatch(cAction))
    restrictAdminAny()(autAction)
  }
}
package controllers

import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}

import javax.inject.Inject

import models.security.{AbstractUser, UserManager, SecurityRoleCache}
import be.objectify.deadbolt.scala.DeadboltActions


class UserProfileController @Inject() (
    deadbolt: DeadboltActions
  ) extends Controller with AuthConfigImpl{

  def index() = Action{
    Redirect(routes.UserProfileController.profile)
  }

  def profile() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val usrFutureOp: Future[Option[AbstractUser]] = currentUser(request)
      usrFutureOp.map { usr =>
        Ok(views.html.userprofile.profile(usr.get))
      }
    }
  }

  def workspace() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val usrFutureOp: Future[Option[AbstractUser]] = currentUser(request)
      usrFutureOp.map { usr =>
        Ok(views.html.userprofile.workspace(usr.get))
      }
    }
  }

  def settings() = deadbolt.SubjectPresent() {
    Action{
      Ok("your settings go here")
    }
  }

  def adminPanel() = Action{
    Redirect(routes.AdminController.listRunnables())
  }


}
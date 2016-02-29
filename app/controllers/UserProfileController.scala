package controllers

import play.api.mvc.{Action, Controller}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import javax.inject.Inject

import models.security.{UserManager, CustomUser, SecurityRoleCache}
import be.objectify.deadbolt.scala.DeadboltActions


class UserProfileController @Inject() (
    myUserManager: UserManager,
    deadbolt: DeadboltActions
  ) extends Controller with AdaAuthConfig {

  // a hook need by auth config
  override val userManager = myUserManager

  def profile() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val usrFutureOp: Future[Option[CustomUser]] = currentUser(request)
      usrFutureOp.map { usrOp =>
        Ok(views.html.userprofile.profile(usrOp.get))
      }
    }
  }

  def workspace() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val usrFutureOp: Future[Option[CustomUser]] = currentUser(request)
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
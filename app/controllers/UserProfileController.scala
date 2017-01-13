package controllers

import java.util.concurrent.TimeoutException
import dataaccess.{RepoException, Criterion}
import models.workspace.Workspace
import Criterion.Infix
import persistence.RepoTypes.UserSettingsRepo
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.i18n.MessagesApi
import play.api.i18n.Messages.Implicits._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import javax.inject.Inject

import models.security.UserManager
import dataaccess.{User => AdaUser}
import security.AdaAuthConfig

import be.objectify.deadbolt.scala.DeadboltActions
import reactivemongo.bson.BSONObjectID


class UserProfileController @Inject() (
    val userManager: UserManager,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    workspaceRepo: UserSettingsRepo
  ) extends Controller with AdaAuthConfig {

  protected val userUpdateForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> text,
      "email" -> ignored("placeholder"),
      //"LDAP DN" -> ignored("placeholder"),
      //"affiliation" -> text,
      "roles" -> ignored(Seq[String]()),
      "permissions" -> ignored(Seq[String]())
    )(AdaUser.apply)(AdaUser.unapply))

  /**
    * Leads to profile page which shows some basic user information.
    */
  def profile() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
        currentUser(request).map { usrOp =>
          Ok(views.html.userprofile.profile(usrOp.get))
        }
    }
  }

  /**
    * Leads to workspace, where user can inspect saved filters.
    */
  def workspace() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val timeout = 12000 millis
      val usrFutureOp: Future[Option[User]] = currentUser(request)
      usrFutureOp.map { (usrOp: Option[User]) =>
        val workspaceFutureTrav: Future[Traversable[Workspace]] = workspaceRepo.find(Seq("email" #== usrOp.get.email))
        val workspaceTrav = Await.result(workspaceFutureTrav, timeout)
        if(workspaceTrav.isEmpty) // TODO: workspace will not be empty in final version!
          Ok(views.html.userprofile.workspace(usrOp.get, new Workspace(None, "dummy", Workspace.emptyUserGroup, Seq(), Seq())))
        else
          Ok(views.html.userprofile.workspace(usrOp.get, workspaceTrav.head))
      }
    }
  }

  /**
    * Leads to user settings, where the user is allowed to change uncritical user properties such as password, affilitiation, name.
    */
  def settings() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val usrFutureOp: Future[Option[User]] = currentUser(request)
      usrFutureOp.map { (usrOp: Option[User]) =>
        usrOp match {
          case Some(usr) => Ok(views.html.userprofile.profileSettings(userUpdateForm.fill(usr)))
          case None => Ok("error")  // not supposed to ever occur due to deadbolt
        }
      }
    }
  }

  /**
    * Save changes made in user settings page to database.
    * Extracts current user from token for information match.
    */
  def updateSettings() = deadbolt.SubjectPresent() {
    Action.async { implicit request =>
      val loggedUserFutureOp: Future[Option[User]] = currentUser(request)

      // integrity check
      loggedUserFutureOp.map{loggedUserOp: Option[User] =>
        if(loggedUserOp.isEmpty){
          Logger.error("Possible user data tempering by unregistered user.")
        }
      }

      val loggedUser: User = Await.result(loggedUserFutureOp, 120000 millis).get
      userUpdateForm.bindFromRequest.fold(
        { formWithErrors =>
          Future.successful(BadRequest(formWithErrors.errors.toString).flashing("failure" -> "An unexpected error occured"))
        },
        (newUserData: User) => {
          updateUserCall(loggedUser, newUserData).map { _ =>
            render {
              case Accepts.Html() => Redirect(routes.UserProfileController.profile()).flashing("success" -> "Profile has been updated")
              case Accepts.Json() => Ok(Json.obj("message" -> "Profile successfully updated"))
            }
          }.recover {
            case t: TimeoutException =>
              Logger.error("Problem found in the update process")
              InternalServerError(t.getMessage)
            case i: RepoException =>
              Logger.error("Problem found in the update process")
              InternalServerError(i.getMessage)
          }
        })
    }
  }


  /**
    * Advanced options only visible to admin user.
    * Simple redirect to runnables in AdminController by now.
    */
  def adminPanel() = Action(
    Redirect(routes.AdminController.listRunnables())
  )

  /**
    * Updates user data by setting unprotected fields to new values.
    *
    * @param refData Reference to fill in for protected fields.
    * @param newData New values, with _id, email, password, roles, permissions being ignored.
    * @return Future(true), if user successfully found and updated in database/ usermanager.
    */
  protected def updateUserCall(refData: User, newData: User): Future[Boolean] = {
    userManager.updateUser(newData)
  }
}
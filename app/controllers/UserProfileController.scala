package controllers

import java.util.concurrent.TimeoutException

import dataaccess.{Criterion, RepoException, User}
import models.workspace.Workspace
import Criterion.Infix
import persistence.RepoTypes.UserSettingsRepo
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller, Request}
import play.api.i18n.MessagesApi
import play.api.i18n.Messages.Implicits._
import views.html.{userprofile => views}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import javax.inject.Inject

import models.security.{DeadboltUser, UserManager}
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.core.WebContext
import reactivemongo.bson.BSONObjectID

class UserProfileController @Inject() (
    workspaceRepo: UserSettingsRepo,
    userManager: UserManager,
    deadbolt: DeadboltActions,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller {

  private implicit def webContext(implicit request: AuthenticatedRequest[_]) = WebContext(messagesApi, webJarAssets)

  protected val userUpdateForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "name" -> text,
      "email" -> ignored("placeholder"),
      //"LDAP DN" -> ignored("placeholder"),
      //"affiliation" -> text,
      "roles" -> ignored(Seq[String]()),
      "permissions" -> ignored(Seq[String]())
    )(User.apply)(User.unapply))

  /**
    * Leads to profile page which shows some basic user information.
    */
  def profile() = deadbolt.SubjectPresent()() {
    implicit request => Future {
      request.subject.map { case DeadboltUser(user) =>
        Ok(views.profile(user))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Leads to workspace, where user can inspect saved filters.
    */
  def workspace() = deadbolt.SubjectPresent()() {
    implicit request =>
      request.subject.map { case DeadboltUser(user) =>
        for {
          workspaceTrav <- workspaceRepo.find(Seq("email" #== user.email))
        } yield {
          if (workspaceTrav.isEmpty) // TODO: workspace will not be empty in final version!
            Ok(views.workspace(user, new Workspace(None, "dummy", Workspace.emptyUserGroup, Seq(), Seq())))
          else
            Ok(views.workspace(user, workspaceTrav.head))
        }
      }.getOrElse(
        Future(BadRequest("The user has not been logged in."))
      )
  }

  /**
    * Leads to user settings, where the user is allowed to change uncritical user properties such as password, affilitiation, name.
    */
  def settings() = deadbolt.SubjectPresent()() {
    implicit request => Future {
      request.subject.map { case DeadboltUser(user) =>
        Ok(views.profileSettings(userUpdateForm.fill(user)))
      }.getOrElse(
        BadRequest("The user has not been logged in.")
      )
    }
  }

  /**
    * Save changes made in user settings page to database.
    * Extracts current user from token for information match.
    */
  def updateSettings() = deadbolt.SubjectPresent()() {
    implicit request =>
      request.subject.map { case DeadboltUser(user) =>
        userUpdateForm.bindFromRequest.fold(
          { formWithErrors =>
            Future(BadRequest(formWithErrors.errors.toString).flashing("failure" -> "An unexpected error occured"))
          },
          (newUserData: User) => {
            updateUserCall(user, newUserData).map { _ =>
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
      }.getOrElse(
        Future(BadRequest("The user has not been logged in."))
      )
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
  protected def updateUserCall(refData: User, newData: User): Future[Boolean] =
    userManager.updateUser(newData)
}
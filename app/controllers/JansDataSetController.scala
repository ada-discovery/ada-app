package controllers

import javax.inject.{Named, Inject}
import persistence.RepoTypeRegistry._

import play.api.mvc.{Action, Controller}
import services.RedCapService

import be.objectify.deadbolt.scala.DeadboltActions

/**
 * PB: Renamed due to the conflicted name
 */
class JansDataSetController @Inject() (
    @Named("DeNoPaCuratedBaselineRepo") denopabaselineRepo: JsObjectCrudRepo,
    @Named("DeNoPaCuratedFirstVisitRepo") denopafirstvisitRepo: JsObjectCrudRepo,
    redCapService: RedCapService,
    deadbolt: DeadboltActions
  ) extends Controller{



  // deadbolt tests
  def restrictedCall = deadbolt.Restrict(List(Array(""))) {
    Action{implicit request =>
      Ok("you are in")
    }
  }


  def notpresent = deadbolt.SubjectNotPresent(){
    Action{
      Ok("this works if not subject present")
    }
  }

  // only visible if logged in
  def present = deadbolt.SubjectPresent(){
    Action{
      Ok("there you are, present subject!")
    }
  }

  // debug: show session
  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }

  // debug: clear session
  def clearsession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }


}
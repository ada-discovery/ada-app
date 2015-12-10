package controllers

import javax.inject.{Named, Inject}
import persistence.RepoTypeRegistry._
import play.api.libs.json.JsObject

import scala.concurrent.duration._
import play.api.mvc.{Action, Controller}



import services.RedCapService

import scala.concurrent.{Await, Future}
import controllers.{ReadonlyController, ExportableAction}



/**
  *
  */
class DataSetController @Inject() (
    @Named("DeNoPaCuratedBaselineRepo") denopabaselineRepo: JsObjectCrudRepo,
    @Named("DeNoPaCuratedFirstVisitRepo") denopafirstvisitRepo: JsObjectCrudRepo,
    redCapService: RedCapService
  ) extends Controller{

  object SetOperation extends Enumeration {
    type SetOperation = Value
    val union, intersection, difference, none = Value
  }


  def index = Action { implicit request =>

    // build history for set operations
    val oldValue = if(request.session.get("setOp").isEmpty) "" else "<a href>" + request.session.get("setOp").get + "</a>"
    val hist = oldValue

    val ops = Array('+', '-', '/')
    val steps = hist.split(ops)

    
    val dicBaseline = denopabaselineRepo.getDictionary
    val dicFirstVisit = denopafirstvisitRepo.getDictionary
    val dicRedCap = redCapService.getDictionary

    val dics = List(dicRedCap, dicBaseline, dicFirstVisit)

    // dummy
    Ok(views.html.DataSetView(dics.toSeq, hist)).withSession(
      request.session + ("setOps" -> "")
    )
  }

  def setOps(set: String = "", operation: String = "") = Action { implicit request =>

    // TODO: format, check and simplify history string
    val oldValue = if(request.session.get("setOp").isEmpty) "" else request.session.get("setOp").get
    val newValue = if (oldValue.length == 0) ("setOp" -> set) else ("setOp" -> (oldValue + operation + set))

    Ok(views.html.DataSetView(List().toSeq, newValue._2)).withSession(
      request.session + newValue
    )
  }


  def showSession = Action { implicit request =>
    Ok(request.session.toString)
  }


  def clearsession = Action { implicit request =>
    Ok("sessions cleared").withNewSession
  }


  def pagecalls = Action { implicit request =>
    var calls = 0
    request.session.get("selections") match {
      case Some(x) => {calls = x.toInt}
      case None => {calls = 0}
    }

    Ok("number of calls: " + calls).withSession(
      request.session + ("calls" -> ((calls + 1).toString))
    )
  }

}

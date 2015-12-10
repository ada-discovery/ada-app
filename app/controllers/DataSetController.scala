package controllers

import javax.inject.{Named, Inject}
import persistence.RepoTypeRegistry._
import play.api.libs.json.JsObject

import scala.concurrent.duration._
import play.api.mvc.{Action, Controller}




import models.Dictionary

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




    val timeout = 120000 millis

    // get records and pass to visualization
    // move this later, as this is rather messy

    val redcapcountFuture = redCapService.countRecords("")
    val redcapcount = Await.result(redcapcountFuture, timeout)

    //val denopabaselinerecordsFuture = denopabaselineRepo.find(None, denopabaselineRepo.toJsonSort("Line_Nr"), None, None, None)
    //val denopabaselinerecords = Await.result(denopabaselinerecordsFuture, timeout)
    val denopaCountFuture = denopabaselineRepo.count(None)
    val denopaCount = Await.result(denopaCountFuture, timeout)



    val dic = redCapService.getDictionary
    val dics = List(dic)


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

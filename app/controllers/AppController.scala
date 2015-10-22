package controllers

import javax.inject.Inject

import play.api.mvc.{Flash, Action, Controller}
import standalone.{InferTypeDeNoPaFirstVisit, InferTypeDeNoPaBaseline, ImportDeNoPaFirstVisit, ImportDeNoPaBaseline}

class AppController extends Controller {
//  @Inject()(
//    importDeNoPaBaseline: ImportDeNoPaBaseline,
//    importDeNoPaFirstVisit: ImportDeNoPaFirstVisit,
//    inferTypeDeNoPaBaseline: InferTypeDeNoPaBaseline,
//    inferTypeDeNoPaFirstVisit: InferTypeDeNoPaFirstVisit
//  )

  def index = Action { implicit request =>
//    println("--------------------")
//    println("BASELINE------------")
//    importDeNoPaBaseline.run
//
//    println("--------------------")
//    println("FIRST VISIT---------")
//    importDeNoPaFirstVisit.run
//
//    println("--------------------")
//    println("BASELINE INFER------")
//    inferTypeDeNoPaBaseline.run
//
//    println("--------------------")
//    println("FIRST VISIT INFER---")
//    inferTypeDeNoPaFirstVisit.run

    Ok(views.html.home())
  }
}

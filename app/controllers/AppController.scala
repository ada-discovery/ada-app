package controllers

import javax.inject.Inject

import play.api.mvc.{Flash, Action, Controller}
import standalone._

class AppController
//  @Inject() (
//    denopaCleanup : DeNoPaCleanup,
//    importDeNoPaBaseline: ImportDeNoPaBaseline,
//    importDeNoPaFirstVisit: ImportDeNoPaFirstVisit,
//    inferTypeDeNoPaBaseline: InferTypeDeNoPaBaseline,
//    inferTypeDeNoPaFirstVisit: InferTypeDeNoPaFirstVisit
//  )
  extends Controller {

  def index = Action { implicit request =>
//    println("Translations")
//    deNoPaTranslations.run

//    println("--------------------")
//    println("IMPORT BASELINE-----")
//    importDeNoPaBaseline.run
//
//    println("--------------------")
//    println("IMPORT FIRST VISIT--")
//    importDeNoPaFirstVisit.run
//
//    println("--------------------")
//    println("BASELINE INFER------")
//    inferTypeDeNoPaBaseline.run
//
//    println("--------------------")
//    println("FIRST VISIT INFER---")
//    inferTypeDeNoPaFirstVisit.run
//
//    println("--------------------")
//    println("CLEANUP-------------")
//    denopaCleanup.run
//
//    println("All done")

    Ok(views.html.home())
  }
}

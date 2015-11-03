package controllers

import javax.inject.Inject

import play.api.mvc.{Flash, Action, Controller}
import standalone._

class AppController extends Controller {

//  @Inject()(
//    deNoPaTranslations: DeNoPaTranslations,
//    denopaCleanup : DeNoPaCleanup
//    //    importDeNoPaBaseline: ImportDeNoPaBaseline,
//    //    importDeNoPaFirstVisit: ImportDeNoPaFirstVisit,
//    //    inferTypeDeNoPaBaseline: InferTypeDeNoPaBaseline,
//    //    inferTypeDeNoPaFirstVisit: InferTypeDeNoPaFirstVisit
//  )
  def index = Action { implicit request =>
//    println("Translations")
//    deNoPaTranslations.run
//
//    println("Cleanup")
//    denopaCleanup.run
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

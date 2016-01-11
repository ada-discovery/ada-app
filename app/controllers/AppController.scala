package controllers

import javax.inject.Inject

import play.api.mvc.{Flash, Action, Controller}
import standalone._
import standalone.denopa.{InferDeNoPaCuratedFirstVisitDictionary, InferDeNoPaCuratedBaselineDictionary, InferDeNoPaFirstVisitDictionary, InferDeNoPaBaselineDictionary}

class AppController
//  @Inject() (
//    inferDeNoPaBaseline: InferDeNoPaBaselineDictionary,
//    inferDeNoPaFirstVisit: InferDeNoPaFirstVisitDictionary,
//    inferDeNoPaCuratedBaseline: InferDeNoPaCuratedBaselineDictionary,
//    inferDeNoPaCuratedFirstVisit: InferDeNoPaCuratedFirstVisitDictionary,
//    importLuxParkDataFromRedCap: ImportLuxParkDataFromRedCap,
//    inferLuxParkDictionary: InferLuxParkDictionary
//  )
  extends Controller {

  def index = Action { implicit request =>
//    println("----------------------------")
//    println("INFER DENOPA BASELINE-------")
//    inferDeNoPaBaseline.run
//
//    println("----------------------------")
//    println("INFER DENOPA FIRST VISIT----")
//    inferDeNoPaFirstVisit.run
//
//    println("----------------------------")
//    println("INFER DENOPA CUR BASELINE---")
//    inferDeNoPaCuratedBaseline.run
//
//    println("----------------------------")
//    println("INFER DENOPA CUR FIRST VISIT")
//    inferDeNoPaCuratedFirstVisit.run
//
//    println("----------------------------")
//    println("IMPORT LUXPARK--------------")
//    importLuxParkDataFromRedCap.run
//
//    println("----------------------------")
//    println("INFER LUXPARK---------------")
//    inferLuxParkDictionary.run

//     println("All done")

    Ok(views.html.home())
  }
}

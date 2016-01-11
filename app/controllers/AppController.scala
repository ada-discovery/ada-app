package controllers

import java.io.File
import javax.inject.Inject

import org.clapper.classutil.ClassFinder
import play.api.i18n.MessagesApi
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

  @Inject var messagesApi: MessagesApi = _

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

  def listRunnables = Action { implicit request =>
    val classpath = List(".").map(new File(_))
    val finder = ClassFinder(classpath)
    val classes = finder.getClasses
    val classMap = ClassFinder.classInfoMap(classes.toIterator)
    val runnables = classes.filter(_.implements("java.lang.Runnable"))
//    classes.foreach(println(_))
//    val plugins = ClassFinder.concreteSubclasses("java.lang.Runnable", classes.toIterator)
//    val plugins = ClassFinder.concreteSubclasses("standalone", classes.toIterator)
    val runnableNames = runnables.map(_.name)

    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.admin.runScripts(runnableNames))
  }
}

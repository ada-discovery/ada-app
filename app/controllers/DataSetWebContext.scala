package controllers

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.core.WebContext
import controllers.dataset._
import play.api.mvc.{Flash, Request}
import play.api.i18n.Messages

class DataSetWebContext(
  val dataSetId: String)(
  implicit val flash: Flash, val msg: Messages, val request: AuthenticatedRequest[_], val webJarAssets: WebJarAssets) {

  val dataSetRouter = new DataSetRouter(dataSetId)
  val dataSetJsRouter = new DataSetJsRouter(dataSetId)
  val dictionaryRouter = new DictionaryRouter(dataSetId)
  val dictionaryJsRouter = new DictionaryJsRouter(dataSetId)
  val categoryRouter = new CategoryRouter(dataSetId)
  val categoryJsRouter = new CategoryJsRouter(dataSetId)
  val filterRouter = new FilterRouter(dataSetId)
  val filterJsRouter = new FilterJsRouter(dataSetId)
  val dataViewRouter = new DataViewRouter(dataSetId)
  val dataViewJsRouter = new DataViewJsRouter(dataSetId)
  val classificationRunRouter = new ClassificationRunRouter(dataSetId)
  val classificationRunJsRouter = new ClassificationRunJsRouter(dataSetId)
  val regressionRunRouter = new RegressionRunRouter(dataSetId)
  val regressionRunJsRouter = new RegressionRunJsRouter(dataSetId)
}

object DataSetWebContext {
  implicit def apply(
    dataSetId: String)(
    implicit context: WebContext
  ) =
    new DataSetWebContext(dataSetId)(context.flash, context.msg, context.request, context.webJarAssets)

  implicit def toFlash(
    implicit webContext: DataSetWebContext
  ): Flash = webContext.flash

  implicit def toMessages(
    implicit webContext: DataSetWebContext
  ): Messages = webContext.msg

  implicit def toRequest(
    implicit webContext: DataSetWebContext
  ): AuthenticatedRequest[_] = webContext.request

  implicit def toWebJarAssets(
    implicit webContext: DataSetWebContext
  ): WebJarAssets = webContext.webJarAssets

  implicit def toWebContext(
    implicit webContext: DataSetWebContext
  ): WebContext = WebContext()

  def dataSetRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetRouter

  def dataSetJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataSetJsRouter

  def dictionaryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryRouter

  def dictionaryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dictionaryJsRouter

  def categoryRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryRouter

  def categoryJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.categoryJsRouter

  def filterRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterRouter

  def filterJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.filterJsRouter

  def dataViewRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewRouter

  def dataViewJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.dataViewJsRouter

  def classificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.classificationRunRouter

  def classificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.classificationRunJsRouter

  def regressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.regressionRunRouter

  def regressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.regressionRunJsRouter
}
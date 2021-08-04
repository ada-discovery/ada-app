package org.ada.web.controllers.dataset

import be.objectify.deadbolt.scala.AuthenticatedRequest
import com.typesafe.config.{ConfigObject, ConfigValue}
import controllers.WebJarAssets
import org.ada.server.AdaException
import java.{util => ju}
import scala.collection.mutable

import org.incal.play.controllers.WebContext
import play.api.Configuration
import play.api.i18n.Messages
import play.api.mvc.Flash

import scala.collection.JavaConversions._
import org.incal.play.routes.CustomDirAssets
import play.twirl.api.Html

class DataSetWebContext(
  val dataSetId: String)(
  implicit val flash: Flash, val msg: Messages, val request: AuthenticatedRequest[_], val webJarAssets: WebJarAssets, val configuration: Configuration) {

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

  // ML routers

  val standardClassificationRunRouter = new StandardClassificationRunRouter(dataSetId)
  val standardClassificationRunJsRouter = new StandardClassificationRunJsRouter(dataSetId)
  val temporalClassificationRunRouter = new TemporalClassificationRunRouter(dataSetId)
  val temporalClassificationRunJsRouter = new TemporalClassificationRunJsRouter(dataSetId)
  val standardRegressionRunRouter = new StandardRegressionRunRouter(dataSetId)
  val standardRegressionRunJsRouter = new StandardRegressionRunJsRouter(dataSetId)
  val temporalRegressionRunRouter = new TemporalRegressionRunRouter(dataSetId)
  val temporalRegressionRunJsRouter = new TemporalRegressionRunJsRouter(dataSetId)
}

object DataSetWebContext {
  implicit def apply(
    dataSetId: String)(
    implicit context: WebContext
  ) =
    new DataSetWebContext(dataSetId)(context.flash, context.msg, context.request, context.webJarAssets, context.configuration)

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

  implicit def configuration(
    implicit webContext: DataSetWebContext
  ): Configuration = webContext.configuration

  implicit def toWebContext(
    implicit webContext: DataSetWebContext
  ): WebContext = WebContext()

  implicit def dataSetRouter(
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

  def standardClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunRouter

  def standardClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardClassificationRunJsRouter

  def temporalClassificationRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunRouter

  def temporalClassificationRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalClassificationRunJsRouter

  def standardRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunRouter

  def standardRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.standardRegressionRunJsRouter

  def temporalRegressionRunRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunRouter

  def temporalRegressionRunJsRouter(
    implicit webContext: DataSetWebContext
  ) = webContext.temporalRegressionRunJsRouter

  // JS Widget Engine

  implicit def jsWidgetEngine(
    implicit webContext: DataSetWebContext
  ): String = jsWidgetEngine(configuration)

  implicit def jsWidgetEngineImports(
    implicit webContext: DataSetWebContext
  ): Html = jsWidgetEngineImports(configuration, toWebJarAssets)

  def jsWidgetEngine(
    configuration: Configuration
  ): String = {
    val engineClassName = getEntrySafe(configuration.getString(_, None), "widget_engine.defaultClassName")
    s"new $engineClassName()"
  }

  def jsWidgetEngineImports(
    configuration: Configuration,
    webJarAssets: WebJarAssets
  ): Html = {
    val engineClassName = getEntrySafe(configuration.getString(_, None), "widget_engine.defaultClassName")

    jsWidgetEngineProvidersMap(configuration, webJarAssets).get(engineClassName).getOrElse(
      throw new AdaException(s"Configuration for a widget engine provider class '${engineClassName}' not found. You must register it first.")
    )
  }

  private def jsWidgetEngineProvidersMap(
    configuration: Configuration,
    webJarAssets: WebJarAssets
  ): Map[String, Html] = {
    val providerConfigs = getEntrySafe(configuration.getObjectList, "widget_engine.providers")

    providerConfigs.map { providerConfig =>
      val className = configValue[String](providerConfig, "className")
      val jsImportConfigs = configValue[ju.ArrayList[ju.HashMap[String, String]]](providerConfig, "jsImports")
      val importsHtml = jsWidgetEngineImports(jsImportConfigs, webJarAssets)
      (className, importsHtml)
    }.toMap
  }

  private val coreWidgetJsPath = "widget-engine.js"

  private def jsWidgetEngineImports(
    jsImportConfigs: Seq[ju.HashMap[String, String]],
    webJarAssets: WebJarAssets
  ): Html = {
    def localScript(path: String) = {
      val src = CustomDirAssets.versioned("javascripts/" + path)
      s"<script type='text/javascript' src='$src'></script>"
    }

    val importsString = jsImportConfigs.map { jsImportConfigJavaMap =>
      val jsImportConfigMap: mutable.Map[String, String] = jsImportConfigJavaMap

      // path
      val path = jsImportConfigMap.getOrElse(
        "path",
        throw new AdaException("The widget engine config. entry 'path' not defined.")
      )

      // check if it's a webjar or a local js
      jsImportConfigMap.get("webjar") match {
        case Some(webjar) =>
          val src = controllers.routes.WebJarAssets.at(webJarAssets.fullPath(webjar, path))

          s"<script src='$src'></script>"
        case None =>
          localScript(path)
      }
    }

    Html((Seq(localScript(coreWidgetJsPath)) ++ importsString).mkString("\n"))
  }

  private def getEntrySafe[T](
    conf: String => Option[T],
    path: String
  ): T =
    conf(path).getOrElse(
      throw new AdaException(s"The widget engine config. entry '$path' not defined.")
    )

  private def configValue[T](
    conf: ConfigObject,
    path: String
  ) =
    Option(conf.get(path)).getOrElse(
      throw new AdaException(s"The widget engine config. entry '$path' not defined.")
    ).unwrapped().asInstanceOf[T]
}
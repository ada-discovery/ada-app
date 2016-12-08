package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers._
import dataaccess.Criterion
import dataaccess.RepoTypes.DataSetSettingRepo
import models.{FieldChartType, DataSetSetting, DataSetFormattersAndIds, ChartType}
import models.DataSetFormattersAndIds.{serializableDataSetSettingFormat, fieldChartTypeFormat, DataSetSettingIdentity, statsCalcSpecFormat}
import models._
import models.FilterShowFieldStyle
import Criterion.Infix
import persistence.RepoTypes._
import persistence.dataset.DataSpaceMetaInfoRepo
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, RequestHeader, Request}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import services.DataSetService
import views.html
import controllers.dataset.routes.{DataSetSettingController => dataSetSettingRoutes}
import controllers.dataset.routes.javascript.{DataSetSettingController => dataSetSettingJsRoutes}

import scala.concurrent.Future
import scala.reflect.ClassTag

class DataSetSettingController @Inject() (
    repo: DataSetSettingRepo,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetService: DataSetService
  ) extends CrudControllerImpl[DataSetSetting, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  private implicit val chartTypeFormatter = EnumFormatter(ChartType)

  private implicit val mapFormatter = MapJsonFormatter.apply
  private implicit val filterShowFieldStyleFormatter = EnumFormatter(FilterShowFieldStyle)
  private implicit val statsCalcSpecFormatter = JsonFormatter[StatsCalcSpec]

  override protected val form = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "dataSetId" -> nonEmptyText,
      "keyFieldName" -> nonEmptyText,
      "exportOrderByFieldName" -> optional(text),
      "defaultScatterXFieldName" -> nonEmptyText,
      "defaultScatterYFieldName" -> nonEmptyText,
      "defaultDistributionFieldName" -> nonEmptyText,
      "defaultDateCountFieldName" -> nonEmptyText,
      "filterShowFieldStyle" -> optional(of[FilterShowFieldStyle.Value]),
      "tranSMARTVisitFieldName" -> optional(text),
      "tranSMARTReplacements" -> default(of[Map[String, String]], Map("\n" -> " ", "\r" -> " ")),
      "cacheDataSet" -> boolean
    ) (DataSetSetting.apply)(DataSetSetting.unapply)
  )

  override protected val home =
    Redirect(routes.DataSetSettingController.find())

  override protected def createView(f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    html.datasetsetting.create(f)

  override protected def showView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    editView(id, f)

  override protected def editView(id: BSONObjectID, f : Form[DataSetSetting])(implicit msg: Messages, request: Request[_]) = {
    val setting = f.value match {
      case Some(setting) => Some(setting)
      case None => result(repo.get(id))
    }
    val form = f.copy(value = setting)
    val fieldNamesCall = new DataSetRouter(setting.get.dataSetId).fieldNames
    html.datasetsetting.editNormal(id, "", form, fieldNamesCall)
  }

  override protected def listView(currentPage: Page[DataSetSetting])(implicit msg: Messages, request: Request[_]) =
    html.datasetsetting.list("", currentPage)

  def editForDataSet(dataSet: String) = restrict {
    Action.async { implicit request =>
      val foundSettingFuture = repo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)
      foundSettingFuture.map { setting =>
        setting.fold(
          NotFound(s"Setting for the data set '#$dataSet' not found")
        ) { entity =>
          implicit val msg = messagesApi.preferred(request)
          val fieldNamesCall = new DataSetRouter(dataSet).fieldNames

          render {
            case Accepts.Html() => {
              val updateCall = dataSetSettingRoutes.updateForDataSet(entity._id.get)
              val cancelCall = new DataSetRouter(dataSet).plainOverviewList
              Ok(html.datasetsetting.edit(
                "",
                fillForm(entity),
                updateCall,
                cancelCall,
                fieldNamesCall,
                result(DataSpaceMetaInfoRepo.allAsTree(dataSpaceMetaInfoRepo)))
              )
            }
            case Accepts.Json() => BadRequest("Edit function doesn't support JSON response. Use get instead.")
          }
        }
      }.recover {
        case t: TimeoutException =>
          Logger.error("Problem found in the edit process")
          InternalServerError(t.getMessage)
      }
    }
  }

  def updateForDataSet(id: BSONObjectID) = restrict {
    Action.async { implicit request =>
      val dataSetIdFuture = repo.get(id).map(_.get.dataSetId)
      dataSetIdFuture.flatMap( dataSetId =>
        update(id, Redirect(new DataSetRouter(dataSetId).plainOverviewList)).apply(request)
      )
    }
  }

//  def addDistributionsToOverview(dataSet: String, fieldNames: Seq[String]) = restrict {
//    processSetting({ setting =>
//      val existingFieldNames = filterSpecsOf[DistributionCalcSpec](setting).map(_.fieldName)
//      val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
//
//      if (newFieldNames.nonEmpty) {
//        val newSetting = setting.copy(statsCalcSpecs = setting.statsCalcSpecs ++ newFieldNames.map(DistributionCalcSpec(_, None)))
//        repo.update(newSetting)
//      } else
//        Future(())
//    }, dataSet)
//  }
//
//  def addBoxPlotsToOverview(dataSet: String, fieldNames: Seq[String]) = restrict {
//    processSetting({ setting =>
//      val existingFieldNames = filterSpecsOf[BoxCalcSpec](setting).map(_.fieldName)
//      val newFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
//
//      if (newFieldNames.nonEmpty) {
//        val newSetting = setting.copy(statsCalcSpecs = setting.statsCalcSpecs ++ newFieldNames.map(BoxCalcSpec(_)))
//        repo.update(newSetting)
//      } else
//        Future(())
//    }, dataSet)
//  }
//
//  def addScatterToOverview(
//    dataSet: String,
//    xFieldName: String,
//    yFieldName: String,
//    groupFieldName: Option[String]
//  ) = restrict {
//    processSetting({ setting =>
//      val existingXYZNames = filterSpecsOf[ScatterCalcSpec](setting).map { spec =>
//        (spec.xFieldName, spec.yFieldName, spec.groupFieldName)
//      }
//      val fieldNames = (xFieldName, yFieldName, groupFieldName)
//      if (!existingXYZNames.contains(fieldNames)) {
//        val newSetting = setting.copy(statsCalcSpecs = setting.statsCalcSpecs ++ Seq(ScatterCalcSpec(xFieldName, yFieldName, groupFieldName)))
//        repo.update(newSetting)
//      } else
//        Future(())
//    }, dataSet)
//  }
//
//  def addCorrelationToOverview(
//    dataSet: String,
//    fieldNames: Seq[String]
//  ) = restrict {
//    processSetting({ setting =>
//      val existingNames = filterSpecsOf[CorrelationCalcSpec](setting).map { spec =>
//        spec.fieldNames
//      }
//      if (!existingNames.contains(fieldNames)) {
//        val newSetting = setting.copy(statsCalcSpecs = setting.statsCalcSpecs ++ Seq(CorrelationCalcSpec(fieldNames)))
//        repo.update(newSetting)
//      } else
//        Future(())
//    }, dataSet)
//  }
//
//  private def filterSpecsOf[T <: StatsCalcSpec](
//    setting: DataSetSetting)(
//    implicit ev: ClassTag[T]
//  ): Seq[T] =
//    setting.statsCalcSpecs.collect{ case t: T => t}
//
//  def addFieldsToOverviewTable(dataSet: String, fieldNames: Seq[String]) = restrict {
//    processSetting({ setting =>
//      val existingFieldNames = setting.listViewTableColumnNames
//      val filteredFieldNames = fieldNames.filter(!existingFieldNames.contains(_))
//      if (filteredFieldNames.nonEmpty) {
//        val newSetting = setting.copy(listViewTableColumnNames = existingFieldNames ++ filteredFieldNames)
//        repo.update(newSetting)
//      } else {
//        Future(())
//      }
//    }, dataSet)
//  }
//
//  protected def processSetting(fun: DataSetSetting => Future[_], dataSet: String) =
//    Action.async { implicit request =>
//      val foundSettingFuture = repo.find(Seq("dataSetId" #== dataSet)).map(_.headOption)
//      foundSettingFuture.flatMap {
//        _.fold(
//          Future(NotFound(s"Setting for the data set '#$dataSet' not found"))
//        ) { setting =>
//          fun(setting).map(_ => Ok("Done"))
//        }
//      }
//    }
//
//  def jsRoutes = restrict{
//    Action { implicit request =>
//      Ok(
//        JavaScriptReverseRouter("jsRoutes")(
//          dataSetSettingJsRoutes.addDistributionsToOverview,
//          dataSetSettingJsRoutes.addBoxPlotsToOverview,
//          dataSetSettingJsRoutes.addScatterToOverview,
//          dataSetSettingJsRoutes.addCorrelationToOverview,
//          dataSetSettingJsRoutes.addFieldsToOverviewTable
//        )
//      ).as("text/javascript")
//    }
//  }
}
package controllers.dataset

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import controllers.{AdminRestrictedCrudController, CrudControllerImpl}
import models.DataSetFormattersAndIds._
import models.{DataSetMetaInfo, DataSpaceMetaInfo, Page}
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.mvc.{Action, Controller}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, RequestHeader, Request}
import play.api.routing.JavaScriptReverseRouter
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil._
import views.html
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.dataset.routes.javascript.{DataSpaceMetaInfoController => dataSpaceMetaInfoJsRoutes}

class DataSpaceMetaInfoController @Inject() (
    repo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    dataSetSettingRepo: DataSetSettingRepo
  ) extends CrudControllerImpl[DataSpaceMetaInfo, BSONObjectID](repo) with AdminRestrictedCrudController[BSONObjectID] {

  override protected val form = Form(
    mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "name" -> nonEmptyText,
    "sortOrder" -> number,
    "timeCreated" -> ignored(new java.util.Date()),
    "dataSetMetaInfos" -> ignored(Seq[DataSetMetaInfo]())
  ) (DataSpaceMetaInfo.apply)(DataSpaceMetaInfo.unapply))

  override protected val home =
    Redirect(routes.DataSpaceMetaInfoController.find())

  override protected def createView(f : Form[DataSpaceMetaInfo])(implicit msg: Messages, request: Request[_]) =
    html.dataspace.create(f)

  override protected def showView(id: BSONObjectID, f: Form[DataSpaceMetaInfo])(implicit msg: Messages, request: Request[_]) = {
    html.dataspace.show(f.value.get, result(getDataSetSizes(f.value.get)), result(repo.find()))
  }

  override protected def editView(id: BSONObjectID, f: Form[DataSpaceMetaInfo])(implicit msg: Messages, request: Request[_]) = {
    html.dataspace.edit(id, f, result(getDataSetSizes(f.value.get)), result(repo.find()))
  }

  override protected def listView(currentPage: Page[DataSpaceMetaInfo])(implicit msg: Messages, request: Request[_]) =
    html.dataspace.list(currentPage)

  override protected def updateCall(item: DataSpaceMetaInfo)(implicit request: Request[AnyContent]) =
    for {
      Some(existingItem) <- repo.get(item._id.get)
      // copy existing data set meta infos
      id <- {
        val requestMap = request.body.asFormUrlEncoded.get
        val ids = requestMap.get("dataSetMetaInfos.id").get
        val newDataSetNames = requestMap.get("dataSetMetaInfos.name").get
        val newDataSetSortOrders = requestMap.get("dataSetMetaInfos.sortOrder").get
        val newHides = requestMap.get("dataSetMetaInfos.hide").get

        val existingDataSetMetaInfos = existingItem.dataSetMetaInfos
        val dataSetMetaInfoIdMap = existingDataSetMetaInfos.map( info => (info._id.get, info)).toMap

        val newDataSetMetaInfos = ((ids, newDataSetNames, newDataSetSortOrders).zipped, newHides).zipped.map{ case ((id, newDataSetName, newDataSetSortOrder), newHide) =>
          val existingDataSetMetaInfo = dataSetMetaInfoIdMap(BSONObjectID(id))

          val newSortOrder = try {
            newDataSetSortOrder.toInt
          } catch {
            // if it's not int use an existing sort order
            case e: NumberFormatException => existingDataSetMetaInfo.sortOrder
          }

          existingDataSetMetaInfo.copy(name = newDataSetName, sortOrder = newSortOrder, hide = newHide.equals("true"))
        }

        repo.update(item.copy(dataSetMetaInfos = newDataSetMetaInfos.toSeq, timeCreated = existingItem.timeCreated))
      }
    } yield
      id

  // if update successful redirect to get/show instead of list
  override def update(id: BSONObjectID) = restrictAdmin(deadbolt) (
    update(id, Redirect(routes.DataSpaceMetaInfoController.get(id)))
  )

  def deleteDataSet(id: BSONObjectID) = restrictAdmin(deadbolt) {
    Action.async{ implicit request =>
      implicit val msg = messagesApi.preferred(request)
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Entity #$id not found"))
      ) { dataSpaceInfo =>
        val requestMap = request.body.asFormUrlEncoded.get
        val dataSetId = requestMap.get("dataSetId").get.head
        val actionChoice = requestMap.get("actionChoice").get.head

        val dsa = dsaf(dataSetId).get

        def unregisterDataSet: Future[_] = {
          val filteredDataSetInfos = dataSpaceInfo.dataSetMetaInfos.filter(!_.id.equals(dataSetId))
          repo.update(dataSpaceInfo.copy(dataSetMetaInfos = filteredDataSetInfos))
        }

        // maybe dropping the entire table/collection would be more appropriate than deleting all the records
        def deleteDataSet: Future[_] =
          dsa.dataSetRepo.deleteAll

        def deleteMetaData: Future[_] =
          for {
            _ <- dsa.fieldRepo.deleteAll
            _ <- dsa.categoryRepo.deleteAll
            setting <- dsa.setting
            _ <- dataSetSettingRepo.delete(setting._id.get)
          } yield
            ()

        val future = actionChoice match {
          case "1" => unregisterDataSet
          case "2" => unregisterDataSet.flatMap(_ => deleteDataSet)
          case "3" => unregisterDataSet.flatMap(_ => deleteDataSet).flatMap(_ => deleteMetaData)
        }

        future.map(_ =>
          Redirect(routes.DataSpaceMetaInfoController.edit(id))
        )
      })
    }
  }

  private def getDataSetSizes(spaceMetaInfo: DataSpaceMetaInfo): Future[Map[String, Int]] = {
    val futures = spaceMetaInfo.dataSetMetaInfos.map { setMetaInfo =>
      val dsa = dsaf(setMetaInfo.id).get
      dsa.dataSetRepo.count().map(size => (setMetaInfo.id, size))
    }
    Future.sequence(futures).map(_.toMap)
  }

  // get is allowed for all logged users
  override def get(id: BSONObjectID) = deadbolt.SubjectPresent()(super.get(id))
}
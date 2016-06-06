package controllers.dataset

import javax.inject.Inject

import controllers.{AdminRestrictedCrudController, CrudControllerImpl}
import models.DataSetFormattersAndIds._
import models.{DataSetMetaInfo, DataSpaceMetaInfo, Page}
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, RequestHeader, Request}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil._
import views.html
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

class DataSpaceMetaInfoController @Inject() (
    repo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory
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
    Redirect(routes.DataSpaceMetaInfoController.listAll())

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

        val existingDataSetMetaInfos = existingItem.dataSetMetaInfos
        val dataSetMetaInfoIdMap = existingDataSetMetaInfos.map( info => (info._id.get, info)).toMap

        val newDataSetMetaInfos = (ids, newDataSetNames, newDataSetSortOrders).zipped.map{ case (id, newDataSetName, newDataSetSortOrder) =>
          val existingDataSetMetaInfo = dataSetMetaInfoIdMap(BSONObjectID(id))

          val newSortOrder = try {
            newDataSetSortOrder.toInt
          } catch {
            // if it's not int use an existing sort order
            case e: NumberFormatException => existingDataSetMetaInfo.sortOrder
          }

          existingDataSetMetaInfo.copy(name = newDataSetName, sortOrder = newSortOrder)
        }

        repo.update(item.copy(dataSetMetaInfos = newDataSetMetaInfos, timeCreated = existingItem.timeCreated))
      }
    } yield
      id

  // if update succesful redirect to get/show instead of list
  override def update(id: BSONObjectID) = restrictAdmin(deadbolt) (
    update(id, Redirect(routes.DataSpaceMetaInfoController.get(id)))
  )

  private def getDataSetSizes(spaceMetaInfo: DataSpaceMetaInfo): Future[Map[String, Int]] = {
    val futures = spaceMetaInfo.dataSetMetaInfos.map { setMetaInfo =>
      val dsa = dsaf(setMetaInfo.id).get
      dsa.dataSetRepo.count().map(size => (setMetaInfo.id, size))
    }
    Future.sequence(futures).map(_.toMap)
  }

  //@Deprecated
  override protected val defaultCreateEntity = DataSpaceMetaInfo(None, "", 0)
}
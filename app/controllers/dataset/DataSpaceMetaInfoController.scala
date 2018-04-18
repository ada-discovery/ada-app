package controllers.dataset

import javax.inject.Inject

import controllers.{AdminRestrictedCrudController, SubjectPresentRestrictedCrudController}
import models._
import DataSetFormattersAndIds._
import controllers.core.{CrudControllerImpl, HasBasicFormCreateView, HasBasicListView}
import dataaccess.RepoTypes.{DataSetSettingRepo, DataSpaceMetaInfoRepo}
import dataaccess.Criterion.Infix
import persistence.dataset.DataSetAccessorFactory
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import reactivemongo.bson.BSONObjectID
import util.SecurityUtil._
import views.html.{dataspace => view}
import play.api.data.Form

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.dataset.routes.javascript.{DataSpaceMetaInfoController => dataSpaceMetaInfoJsRoutes}
import dataaccess.User
import services.DataSpaceService

import scala.collection.mutable.Buffer

class DataSpaceMetaInfoController @Inject() (
    repo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory,
    dataSetSettingRepo: DataSetSettingRepo,
    dataSpaceService: DataSpaceService
  ) extends CrudControllerImpl[DataSpaceMetaInfo, BSONObjectID](repo)
    with SubjectPresentRestrictedCrudController[BSONObjectID]
    with HasBasicFormCreateView[DataSpaceMetaInfo]
    with HasBasicListView[DataSpaceMetaInfo] {

  override protected[controllers] val form = Form(
    mapping(
    "id" -> ignored(Option.empty[BSONObjectID]),
    "name" -> nonEmptyText,
    "sortOrder" -> number,
    "timeCreated" -> ignored(new java.util.Date()),
    "dataSetMetaInfos" -> ignored(Seq[DataSetMetaInfo]())
  ) (DataSpaceMetaInfo(_, _, _, _, _))(
      (item: DataSpaceMetaInfo) =>
        Some((item._id, item.name, item.sortOrder, item.timeCreated, item.dataSetMetaInfos))
  ))

  override protected val home =
    Redirect(routes.DataSpaceMetaInfoController.find())

  // create view

  override protected[controllers] def createView = { implicit ctx => view.create(_) }

  // show view

  override protected type ShowViewData = (DataSpaceMetaInfo, Map[String, Int], Traversable[DataSpaceMetaInfo])

  override protected def getFormShowViewData(
    id: BSONObjectID,
    form: Form[DataSpaceMetaInfo]
  ) = { request =>
    getFormEditViewData(id, form)(request).map { case (_, newForm, dataSetSizes, tree) =>
      val dataSpace = newForm.value.getOrElse(form.get.copy(dataSetMetaInfos = Nil))
      dataSpace.children.clear()
      (dataSpace, dataSetSizes, tree)
    }
  }

  override protected[controllers] def showView = { implicit ctx =>
    (view.show(_, _, _)).tupled
  }

  // edit view and data

  override protected type EditViewData = (
    BSONObjectID,
    Form[DataSpaceMetaInfo],
    Map[String, Int],
    Traversable[DataSpaceMetaInfo]
  )

  override protected def getFormEditViewData(
    id: BSONObjectID,
    form: Form[DataSpaceMetaInfo]
  ) = { request =>
    val treeFuture = dataSpaceService.getTreeForCurrentUser(request)
    val childrenFuture = repo.find(Seq("parentId" #== id))

    for {
      // get all the data spaces
      all <- treeFuture

      // get the children of this data space (data sets)
      children <- childrenFuture

      // filter data sets and spaces available for the currently logged user
      filteredDataSpace <- {
        val dataSpace = form.value.get
        dataSpace.children.appendAll(children)
        dataSpaceService.getDataSpaceForCurrentUser(dataSpace)(request)
      }

      // calc the sizes of the children data sets
      dataSetSizes <- filteredDataSpace match {
        case None => Future(Map[String, Int]())
        case Some(dataSpace) => getDataSetSizes(dataSpace)
      }
    } yield {
      (id, form.copy(value = filteredDataSpace), dataSetSizes, all)
    }
  }

  override protected[controllers] def editView = { implicit ctx =>
    (view.edit(_, _, _, _)).tupled
  }

  override def edit(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) (
    toAuthenticatedAction(
      super.edit(id)
    )
  )

  // list view

  override protected[controllers] def listView = { implicit ctx => view.list(_) }

  override protected def updateCall(item: DataSpaceMetaInfo)(implicit request: Request[AnyContent]) =
    for {
      Some(existingItem) <- repo.get(item._id.get)
      // copy existing data set meta infos
      newDataSetMetaInfos = {
        val requestMap = request.body.asFormUrlEncoded.get
        val ids = requestMap.get("dataSetMetaInfos.id").get
        val newDataSetNames = requestMap.get("dataSetMetaInfos.name").get
        val newDataSetSortOrders = requestMap.get("dataSetMetaInfos.sortOrder").get
        val newHides = requestMap.get("dataSetMetaInfos.hide").get

        val existingDataSetMetaInfos = existingItem.dataSetMetaInfos
        val dataSetMetaInfoIdMap = existingDataSetMetaInfos.map( info => (info._id.get, info)).toMap

        ((ids, newDataSetNames, newDataSetSortOrders).zipped, newHides).zipped.map{ case ((id, newDataSetName, newDataSetSortOrder), newHide) =>
          val existingDataSetMetaInfo = dataSetMetaInfoIdMap(BSONObjectID(id))

          val newSortOrder = try {
            newDataSetSortOrder.toInt
          } catch {
            // if it's not int use an existing sort order
            case e: NumberFormatException => existingDataSetMetaInfo.sortOrder
          }

          existingDataSetMetaInfo.copy(name = newDataSetName, sortOrder = newSortOrder, hide = newHide.equals("true"))
        }
      }

      // update the data space meta info
      id <-
        repo.update(item.copy(dataSetMetaInfos = newDataSetMetaInfos.toSeq, timeCreated = existingItem.timeCreated))

      // update the individual data set meta infos
      _ <- Future.sequence(
          newDataSetMetaInfos.map( newDataSetMetaInfo =>
            dsaf(newDataSetMetaInfo.id).map { dsa =>
              dsa.updateMetaInfo(newDataSetMetaInfo)
            }.getOrElse(
              Future(())
            )
          )
        )
    } yield
      id

  // if update successful redirect to get/show instead of list
  override def update(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) (
    toAuthenticatedAction(
      update(id, Redirect(routes.DataSpaceMetaInfoController.get(id)))
    )
  )

  def deleteDataSet(id: BSONObjectID) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
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
          for {
            _ <- dsa.updateDataSetRepo
            _ <- dsa.dataSetRepo.deleteAll
          } yield
            ()

        def deleteFields: Future[_] =
          dsa.fieldRepo.deleteAll

        def deleteCategories: Future[_] =
          dsa.categoryRepo.deleteAll

        def deleteSetting: Future[_] =
          dsa.setting.flatMap ( setting =>
            dataSetSettingRepo.delete(setting._id.get)
          )

        val future = actionChoice match {

          case "1" =>
            unregisterDataSet

          case "2" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
          } yield ()

          case "3" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
            _ <- deleteFields
            _ <- deleteCategories
          } yield ()

          case "4" => for {
            _ <- unregisterDataSet
            _ <- deleteDataSet
            _ <- deleteFields
            _ <- deleteCategories
            _ <- deleteSetting
          } yield ()
        }

        future.map(_ =>
          Redirect(routes.DataSpaceMetaInfoController.edit(id))
        )
      })
  }

  private def getDataSetSizesRecurrently(
    spaceMetaInfo: DataSpaceMetaInfo
  ): Future[Map[String, Int]] = {
    val singleMapfuture = getDataSetSizes(spaceMetaInfo)
    val recFutures = spaceMetaInfo.children.map(getDataSetSizes)

    for {
      simpleMap <- singleMapfuture
      mergeSubMap <-
        Future.sequence(recFutures).map { maps =>
        maps.foldLeft(Map[String, Int]()) { case (a, b) =>
          a ++ b
        }
      }
    } yield {
      simpleMap ++ mergeSubMap
    }
  }

  private def getDataSetSizes(
    spaceMetaInfo: DataSpaceMetaInfo
  ): Future[Map[String, Int]] = {
    val futures = spaceMetaInfo.dataSetMetaInfos.map { setMetaInfo =>
      val dsa = dsaf(setMetaInfo.id).get
      dsa.dataSetRepo.count().map(size => (setMetaInfo.id, size))
    }
    Future.sequence(futures).map(_.toMap)
  }

  //  // get is allowed for all logged users
//  override def get(id: BSONObjectID) = deadbolt.SubjectPresent()(super.get(id))
}
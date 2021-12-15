package org.ada.web.controllers.dataset.omics


import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.{DataSetInfo, DataSetType}
import org.ada.server.models.Filter.FilterOrId
import org.ada.web.controllers.core.{AdaBaseController, AdaReadonlyControllerImpl}
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion._
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.{AsyncReadonlyRepo, Criterion}
import org.incal.play.Page
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class OmicsDataSetController @Inject()(
   dataSetSettingRepo: DataSetSettingRepo,
   dataSpaceService: DataSpaceService,
   dataSetAccessorFactory: DataSetAccessorFactory) extends AdaBaseController
{


  private def dataSetRequestPermission(dataSetId: String) = s"DS:$dataSetId.dataSet"

  def dataSetIds(currentDataSetId: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(currentDataSetId)) { implicit request =>
     val mInfosFiltered = for {
      dataSettings <- dataSetSettingRepo.find(Seq("dataSetInfo" #!= None, "dataSetId" #!= currentDataSetId))
      dataSetMetaInfos <- dataSpaceService.getDataSetMetaInfosForCurrentUser
    } yield for {
      dSettings <- dataSettings
      mInfos <- dataSetMetaInfos
      if dSettings.dataSetId == mInfos.id &&
         dSettings.dataSetInfo.get.dataSetType == DataSetType.omics
    } yield mInfos

    for {mInfoFilter <- mInfosFiltered} yield {
      val dataSetIdJsons = mInfoFilter.map { metaInfo =>
        Json.obj(
          "name" -> metaInfo.id,
          "label" -> metaInfo.id
        )
      }
      Ok(Json.toJson(dataSetIdJsons))
    }
  }


  def getFieldsNamesAndLabels(dataSetIdToSearch: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(dataSetIdToSearch)) { implicit request =>
      val dsa = dataSetAccessorFactory.applySync(dataSetIdToSearch).get
      for{
        fields <- dsa.fieldRepo.find()
      } yield {
        val fieldNameAndLabels = fields.map { field => Json.obj("name" -> field.name, "label" -> field.label)}.toSeq
          Ok(Json.toJson(fieldNameAndLabels))
      }

    }


  def getDataviews(dataSetIdToSearch: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(dataSetIdToSearch)) { implicit request =>
      val dsa = dataSetAccessorFactory.applySync(dataSetIdToSearch).get
      for {
         dataViews <- dsa.dataViewRepo.find()
      } yield {
        val dataViewsJson = dataViews.filter(!_.isPrivate).map { dataView =>
          Json.obj("name" -> dataView._id.get.stringify, "label" -> dataView.name)}.toSeq
          Ok(Json.toJson(dataViewsJson))
        }

    }


  /*def searchInOmics(currentDataSetId: String,
                        filterOrIdCurrentDatSet: FilterOrId,
                        dataSetIdToSearch: String,
                        dataViewId: BSONObjectID,
                        searchField: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(dataSetIdToSearch)) { implicit request =>

      val currentRepo = dataSetAccessorFactory.applySync(currentDataSetId).get
      var currentDataSetRepo = currentRepo.dataSetRepo
      //val repoToSearch = dataSetAccessorFactory.applySync(dataSetIdToSearch).get

      for {
        dataSettings <- dataSetSettingRepo.find(Seq("dataSetId" #== currentDataSetId, "dataSetInfo" #!= None))
        resolvedFilter <- currentRepo.filterRepo.resolve(filterOrIdCurrentDatSet)
        criteria <- toCriteria(resolvedFilter.conditions)
        result <- currentDataSetRepo.find(criteria = criteria, projection = Seq(dataSettings.head.dataSetInfo.get.dataSetJoinIdName))
      } yield {
        Ok(Json.obj("Res" -> result.toSeq))
      }

    }

  private def toCriteria(filter: Seq[FilterCondition]): Future[Seq[Criterion[Any]]] = {
    val fieldNames = filter.seq.map(_.fieldName)
    filterValueConverters(fieldNames).map(
      FilterCondition.toCriteria(_, filter)
    )
  }

  private def filterValueConverters(fieldNames: Traversable[String]): Future[Map[String, String => Option[Any]]] =
    Future(Map())*/


}

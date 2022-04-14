package org.ada.web.controllers.dataset.omics


import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.models.DataSetType
import org.ada.server.models.Filter.FilterOrId
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion._
import org.incal.core.{ConditionType, FilterCondition}
import play.api.cache.CacheApi
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import play.cache.NamedCache

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._



class OmicsDataSetController @Inject()(
   dataSetSettingRepo: DataSetSettingRepo,
   dataSpaceService: DataSpaceService,
   dataSetAccessorFactory: DataSetAccessorFactory,
   @NamedCache("filters-cache") filtersCache: CacheApi) extends AdaBaseController
{


  private def dataSetRequestPermission(dataSetId: String) = s"DS:$dataSetId.dataSet.getView"

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

  def getDataSetInfo(dataSetIdToSearch: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(dataSetIdToSearch)) { implicit request =>
      for {
        dataSettings <- dataSetSettingRepo.find(criteria = Seq("dataSetId" #== dataSetIdToSearch, "dataSetInfo" #!= None))
      } yield {
        Ok(Json.toJson(dataSettings.head.dataSetInfo))
      }
    }


  def cacheFilterOrIds(filterOrId: FilterOrId, currentDataSetId: String): Action[AnyContent] =
    restrictAdminOrPermissionAny(dataSetRequestPermission(currentDataSetId)){ implicit request =>
        val filterTmpId =  UUID.randomUUID().toString
        filtersCache.set(filterTmpId, filterOrId, 3.seconds)
        Future(Ok(Json.obj("filterTmpId" -> filterTmpId)))
  }

  def searchInOmics(currentDataSetId: String,
                        filterOrIdCurrentDatSet: FilterOrId,
                        dataSetIdToSearch: String,
                        searchField: String) =
    restrictAdminOrPermissionAny(dataSetRequestPermission(dataSetIdToSearch)) { implicit request =>

      val currentRepo = dataSetAccessorFactory.applySync(currentDataSetId).get
      val currentDisplayedDataSetRepo = currentRepo.dataSetRepo
      val filterRepo = currentRepo.filterRepo
      for {
        dataSettings <- dataSetSettingRepo.find(Seq("dataSetId" #== currentDataSetId, "dataSetInfo" #!= None))
        resolvedFilter <- filterRepo.resolve(filterOrIdCurrentDatSet)
        criteria <- toCriteria(resolvedFilter.conditions)
        result <- currentDisplayedDataSetRepo.find(criteria = criteria, projection = Seq(dataSettings.head.dataSetInfo.get.dataSetJoinIdName))
      } yield {
        val values = result.map(res => (res \ dataSettings.head.dataSetInfo.get.dataSetJoinIdName).as[String]).mkString(",")
        val filterConditions = Seq(FilterCondition(searchField, None,
                                    conditionType = ConditionType.In,
                                    value = Option(if(values.isEmpty) "No results found from data set : " + currentDataSetId else values),
                                    None))
        val omicsFilterTmpId = UUID.randomUUID().toString
        filtersCache.set(omicsFilterTmpId, filterConditions, 3.seconds)
        Ok(Json.obj("omicsFilterTmpId" -> omicsFilterTmpId))
      }

    }

  private def toCriteria(filter: Seq[FilterCondition]): Future[Seq[Criterion[Any]]] = {
    val fieldNames = filter.seq.map(_.fieldName)
    filterValueConverters(fieldNames).map(
      FilterCondition.toCriteria(_, filter)
    )
  }

  private def filterValueConverters(fieldNames: Traversable[String]): Future[Map[String, String => Option[Any]]] =
    Future(Map())


}

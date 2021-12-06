package org.ada.web.controllers.dataset.omics


import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.DataSetType
import org.ada.web.controllers.core.AdaBaseController
import org.ada.web.services.DataSpaceService
import org.incal.core.dataaccess.Criterion._
import play.api.libs.json.{JsArray, Json}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global



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

}

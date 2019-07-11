package org.ada.server.runnables.core

import javax.inject.{Inject, Named}
import org.ada.server.dataaccess.ElasticJsonCrudRepoFactory
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, DataSpaceMetaInfoRepo}
import org.ada.server.dataaccess.elastic.format.ElasticIdRenameUtil
import org.ada.server.models.StorageType
import org.incal.access.elastic.ElasticCrudRepoExtra
import org.incal.core.dataaccess.Criterion._
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnableExt, RunnableHtmlOutput}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class List08ElasticDataSetsToMigrate @Inject()(
  dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
) extends FutureRunnable with List08ElasticDataSetsToMigrateHelper with RunnableHtmlOutput {

  override def runAsFuture =
    for {
      dataSetMetaInfos <- dataSpaceMetaInfoRepo.find().map(_.flatMap(_.dataSetMetaInfos))
      allDataSetIds = dataSetMetaInfos.map(_.id).toSeq
      flaggedDataSetIds <- dataSetIdsToMigrate(allDataSetIds)
    } yield {
      addParagraph(s"<h4>Found ${flaggedDataSetIds.size} Elastic data sets out of ${allDataSetIds.size} which need to be migrated:</h4>")
      flaggedDataSetIds.foreach(addParagraph)
    }
}

trait List08ElasticDataSetsToMigrateHelper {

  @Inject var dataSetSettingRepo: DataSetSettingRepo = _
  @Inject @Named("ElasticJsonCrudRepoFactory") var elasticDataSetRepoFactory: ElasticJsonCrudRepoFactory = _

  protected def dataSetIdsToMigrate(dataSetIds: Seq[String]) =
    for {
      dataSetSettings <- dataSetSettingRepo.find(Seq("dataSetId" #-> dataSetIds, "storageType" #== StorageType.ElasticSearch.toString))
      dataSetIds = dataSetSettings.map(_.dataSetId)

      flaggedDataSetIds <- Future.sequence(
        dataSetIds.map { dataSetId =>
          isElasticIdKeyword(dataSetId).map(isKeywordId => (dataSetId, isKeywordId))
        }
      )
    } yield
      flaggedDataSetIds.filter(!_._2).map(_._1)

  private def isElasticIdKeyword(dataSetId: String) = {
    val indexName = "data-" + dataSetId
    val jsonRepo = elasticDataSetRepoFactory(indexName, indexName, Nil, None, false).asInstanceOf[ElasticCrudRepoExtra]

    for {
      mappings <- jsonRepo.getMappings
    } yield
      mappings.headOption.flatMap(_._2.get(ElasticIdRenameUtil.storedIdName)).map { idProperties =>
        idProperties match {
          case map: Map[String, Any] => map.get("type").map(_.equals("keyword")).getOrElse(false)
          case _ => false
        }
      }.getOrElse(false)
  }
}
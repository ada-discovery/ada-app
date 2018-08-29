package runnables.ppmi

import javax.inject.Inject

import _root_.util.{GroupMapList, seqFutures}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.StorageType
import org.incal.core.dataaccess.Criterion._
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsObject, _}
import runnables.FutureRunnable
import services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global

class MergePPMIClinicalDataSetByVisit @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends FutureRunnable {

  private val logger = Logger // (this.getClass())

  private val dataSetId = "ppmi.clinical_visit"
  private val newDataSetId = "ppmi.clinical_visit_test_flat"
  private val newDataSetName = "PPMI Clinical Visit Test Flat"

  private val subjectIdFieldName = "PATNO"
  private val visitFieldName = "VISIT_NAME"
  private val pageNameFieldName = "PAG_NAME"

  private val subjectProcessingGroupSize = 10

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).get
    val repo = dsa.dataSetRepo
    val fieldRepo = dsa.fieldRepo

    for {
      // register a data set
      newDsa <- dataSetService.register(dsa, newDataSetId, newDataSetName, StorageType.ElasticSearch)

      // get all the fields
      fields <- fieldRepo.find()

      // save the dictionary
      _ <- dataSetService.updateDictionaryFields(newDsa.fieldRepo, fields, false, true)

      // get the subject ids
      subjectIds <- repo.find(projection = Seq(subjectIdFieldName)).map(_.map(json =>
        (json \ subjectIdFieldName).as[Int]
      ).toSet)

      // delete all the records from the new data set
      _ <- newDsa.dataSetRepo.deleteAll

      // proceed by subjects, merge records and save
      _ <- seqFutures(subjectIds.toSeq.grouped(subjectProcessingGroupSize)) { subjectIds =>
        for {
          jsons <- repo.find(criteria = Seq(subjectIdFieldName #-> subjectIds))
        } yield {
          val subjectVisitJsons = jsons.map { json =>
            val subjectId = (json \ subjectIdFieldName).as[Int]
            val visit = (json \ visitFieldName).asOpt[String]
            ((subjectId, visit), json)
          }.toGroupMap

          val newJsons = subjectVisitJsons.map { case (_, jsons) =>
            jsons.foldLeft(Json.obj())(_++_).-(JsObjectIdentity.name)
          }

          // update
          newDsa.dataSetRepo.save(newJsons)
        }
      }
    } yield
      ()
  }
}
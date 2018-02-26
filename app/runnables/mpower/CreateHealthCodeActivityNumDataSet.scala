package runnables.mpower

import javax.inject.Inject

import models.{Field, FieldTypeId, StorageType}
import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json._
import runnables.{FutureRunnable, InputFutureRunnable}
import services.DataSetService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CreateHealthCodeActivityNumDataSet @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[CreateHealthCodeActivityNumDataSetSpec] {

  private val healthCodeFieldName = "healthCode"
  private val recordNumFieldName = "recordNum"
  private val recordNumField = Field(recordNumFieldName, Some("Record Num"), FieldTypeId.Integer)

  override def runAsFuture(spec: CreateHealthCodeActivityNumDataSetSpec) = {
    val dsa = dsaf(spec.dataSetId).get
    val newDataSetId = spec.dataSetId + "_records_num"

    for {
      // get the name of the source data set
      dataSetName <- dsa.dataSetName

      // retrieve all the health codes
      healthCodeJsons <- dsa.dataSetRepo.find(projection = Seq(healthCodeFieldName)).map { jsons =>
        jsons.map(json =>
          (json \ healthCodeFieldName).get
        )
      }

      healthCodeField <- dsa.fieldRepo.get(healthCodeFieldName)

      // register the target dsa
      targetDsa <- dataSetService.register(
        dsa,
        newDataSetId,
        dataSetName + " Records num",
        StorageType.ElasticSearch,
        healthCodeFieldName
      )

      // create a new dictionary
      _ <- dataSetService.updateDictionaryFields(
        newDataSetId,
        Seq(healthCodeField.getOrElse(throw new IllegalArgumentException("No health code field found")),
        recordNumField),
        false,
        true
      )

      // delete the old data (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the new data
      _ <- {
        val newJsons = healthCodeJsons.groupBy(identity).map{ case (healthCode, items) =>
          Json.obj(healthCodeFieldName -> healthCode, recordNumFieldName -> items.size)
        }
        targetDsa.dataSetRepo.save(newJsons)
      }
    } yield
      ()
  }

  override def inputType = typeOf[CreateHealthCodeActivityNumDataSetSpec]
}

case class CreateHealthCodeActivityNumDataSetSpec(
  dataSetId: String
)
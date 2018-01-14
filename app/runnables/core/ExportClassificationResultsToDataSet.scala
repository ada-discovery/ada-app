package runnables.core

import javax.inject.Inject

import dataaccess.{ClassificationResultRepoFactory, FilterRepoFactory}
import models.ml.ClassificationResult
import models.ml.ClassificationResult.classificationResultFormat
import models.{AdaException, StorageType}
import persistence.RepoTypes.ClassificationRepo
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import runnables.InputFutureRunnable
import services.DataSetService
import util.FieldUtil.caseClassToFlatFieldTypes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

@Deprecated
class ExportClassificationResultsToDataSet @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    classificationResultRepoFactory: ClassificationResultRepoFactory,
    filterRepoFactory: FilterRepoFactory,
    classificationRepo: ClassificationRepo
  ) extends InputFutureRunnable[ExportClassificationResultsToDataSetSpec] {

  private val logger = Logger // (this.getClass())

  private val fields = caseClassToFlatFieldTypes[ClassificationResult]("-", Set("_id"))

  private case class ClassificationResultExtra(dataSetId: String, mlModelName: Option[String], filterName: Option[String])
  private implicit val classificationResultExtraFormat = Json.format[ClassificationResultExtra]

  private val extraFields = caseClassToFlatFieldTypes[ClassificationResultExtra]()

  override def runAsFuture(input: ExportClassificationResultsToDataSetSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    val targetDataSetId = input.dataSetId + "_classification"

    for {
      // collect all the results
      allResults <- classificationResults(dsa)

      // data set name
      dataSetName <- dsa.dataSetName

      // register target dsa
      targetDsa <- dataSetService.register(dsa, targetDataSetId, dataSetName + " Classification", StorageType.Mongo, "timeCreated")

      // update the dictionary
      _ <- dataSetService.updateDictionary(targetDataSetId, fields ++ extraFields, false, true)

      // delete the old results (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the results
      _ <- targetDsa.dataSetRepo.save(
        allResults.map { case (result, extraResult) =>
          val resultJson = Json.toJson(result).as[JsObject]
          val extraResultJson = Json.toJson(extraResult).as[JsObject]
          resultJson ++ extraResultJson
        }
      )
    } yield
      ()
  }

  private def classificationResults(
    dsa: DataSetAccessor
  ): Future[Traversable[(ClassificationResult, ClassificationResultExtra)]] = {
    for {
      // get the results
      results <- dsa.classificationResultRepo.find()

      // add some extra stuff for easier reference (model and filter name)
      resultsWithExtra <- Future.sequence(
        results.map { result =>
          val classificationFuture = classificationRepo.get(result.setting.mlModelId)
          val filterFuture = result.setting.filterId.map(dsa.filterRepo.get).getOrElse(Future(None))

          for {
            mlModel <- classificationFuture
            filter <- filterFuture
          } yield
            (result, ClassificationResultExtra(dsa.dataSetId, mlModel.flatMap(_.name), filter.flatMap(_.name)))
          }
      )
    } yield
      resultsWithExtra
  }

  override def inputType = typeOf[ExportClassificationResultsToDataSetSpec]
}

case class ExportClassificationResultsToDataSetSpec(dataSetId: String)
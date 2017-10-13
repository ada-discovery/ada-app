package runnables.mpower

import javax.inject.Inject

import dataaccess.{ClassificationResultRepoFactory, FilterRepoFactory}
import models.ml.ClassificationResult
import models.ml.ClassificationResult.classificationResultFormat
import models.{AdaException, StorageType}
import persistence.RepoTypes.ClassificationRepo
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import runnables.InputFutureRunnable
import services.DataSetService
import util.FieldUtil.caseClassToFlatFieldTypes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class MergeRCClassificationResults @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    classificationResultRepoFactory: ClassificationResultRepoFactory,
    filterRepoFactory: FilterRepoFactory,
    classificationRepo: ClassificationRepo
  ) extends InputFutureRunnable[MergeRCClassificationResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  private val fields = caseClassToFlatFieldTypes[ClassificationResult]("-", Set("_id"))

  private case class ClassificationResultExtra(dataSetId: String, mlModelName: Option[String], filterName: Option[String])
  private implicit val classificationResultExtraFormat = Json.format[ClassificationResultExtra]

  private val extraFields = caseClassToFlatFieldTypes[ClassificationResultExtra]()

  private val groupSize = 10

  override def runAsFuture(input: MergeRCClassificationResultsSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    val targetDataSetId = input.dataSetId + "_classification"

    for {
      // get the data set ids
      jsons <- dsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // collect all the results
      allResults <- util.seqFutures(dataSetIds.grouped(groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          classificationResults(id)
        })
      }

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
        allResults.flatten.flatten.map { case (result, extraResult) =>
          val resultJson = Json.toJson(result).as[JsObject]
          val extraResultJson = Json.toJson(extraResult).as[JsObject]
          resultJson ++ extraResultJson
        }
      )
    } yield
      ()
  }

  private def classificationResults(dataSetId: String): Future[Traversable[(ClassificationResult, ClassificationResultExtra)]] = {
    val classificationResultRepo = classificationResultRepoFactory(dataSetId)
    val filterRepo = filterRepoFactory(dataSetId)

    for {
      // get the results
      results <- classificationResultRepo.find()

      // add some extra stuff for easier reference (model and filter name)
      resultsWithExtra <- Future.sequence(
        results.map { result =>
          val classificationFuture = classificationRepo.get(result.setting.mlModelId)
          val filterFuture = result.setting.filterId.map(filterRepo.get).getOrElse(Future(None))

          for {
            mlModel <- classificationFuture
            filter <- filterFuture
          } yield
            (result, ClassificationResultExtra(dataSetId, mlModel.flatMap(_.name), filter.flatMap(_.name)))
          }
      )
    } yield
      resultsWithExtra
  }

  override def inputType = typeOf[MergeRCClassificationResultsSpec]
}

case class MergeRCClassificationResultsSpec(dataSetId: String)
package runnables.core

import javax.inject.Inject

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import dataaccess.StreamSpec
import dataaccess.JsonCrudRepoExtra._
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models._
import models.ml.{DerivedDataSetSpec, RenameFieldsSpec}
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import services.{DataSetService, DataSpaceService}
import util.nonAlphanumericToUnderscore

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf
import scala.util.Random

class FixNonalphanumericFields @Inject() (
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSpaceService: DataSpaceService
  ) extends InputFutureRunnable[FixNonalphanumericFieldsSpec] {

  private val logger = Logger
  private val nonAlphanumericUnderscorePattern = "[^A-Za-z0-9_]".r  // [^\\p{Alnum}]".r // + _, u002e
  private val escapedDotString = "u002e"

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(input: FixNonalphanumericFieldsSpec) = {
    logger.info(s"Fixing the non-alphanumeric fields of the data set ${input.dataSetId}.")

    val dsa = dsaf(input.dataSetId).get
    val newDataSetId = input.dataSetId + "_temporary_" + Random.nextInt()
    val streamSpec = StreamSpec(input.batchSize)

    for {
      // original count
      originalCount <- dsa.dataSetRepo.count()

      // get all the fields
      fields <- dsa.fieldRepo.find()

      // create a map old -> new field names
      oldToNewFieldNameMap = {
        val fieldsToFix = fields.filter(field => nonAlphanumericUnderscorePattern.findFirstIn(field.name).isDefined || field.name.contains(escapedDotString))

        fieldsToFix.map { field =>
          val newFieldName = nonAlphanumericToUnderscore(field.name.replaceAllLiterally(escapedDotString, "_"))
          (field.name, newFieldName)
        }.toMap
      }

      // rename fields and move data to a temporary data set
      _ <- dataSetService.renameFields(
        RenameFieldsSpec(
          input.dataSetId, oldToNewFieldNameMap, DerivedDataSetSpec(newDataSetId, "Temporary (To Delete)", StorageType.ElasticSearch), streamSpec
        ))

      movedDsa = dsaf(newDataSetId).get

      // check the counts

      _ <- movedDsa.dataSetRepo.flushOps

      tempCount <- movedDsa.dataSetRepo.count()

      _ <- Future {
        if (originalCount != tempCount)
          throw new AdaException(s"Terminating 'FixNonalphanumericFields' script due to mismatched counts: $originalCount (original) vs. $tempCount (temp).")
      }

      // fields

      newFields = fields.map { field =>
        val newFieldName = oldToNewFieldNameMap.getOrElse(field.name, field.name)
        field.copy(name = newFieldName)
      }

      _ <- dsa.fieldRepo.deleteAll

      _ <- dataSetService.updateDictionaryFields(dsa.fieldRepo, newFields, true, true)

      // filters

      filters <- dsa.filterRepo.find()

      newFilters = filters.map { filter =>
        val newConditions = filter.conditions.map { condition =>
          val newFieldName = oldToNewFieldNameMap.getOrElse(condition.fieldName, condition.fieldName)
          condition.copy(fieldName = newFieldName)
        }
        filter.copy(conditions = newConditions)
      }

      _ <- dsa.filterRepo.update(newFilters)

      // views

      views <- dsa.dataViewRepo.find()

      newViews = {
        def replace(fieldName: String) = oldToNewFieldNameMap.getOrElse(fieldName, fieldName)

        views.map { view =>
          val newTableColumnNames = view.tableColumnNames.map(replace)

          val newSpecs = view.widgetSpecs.map { widgetSpec =>
            widgetSpec match {
              case spec: DistributionWidgetSpec => spec.copy(fieldName = replace(spec.fieldName), groupFieldName = spec.groupFieldName.map(replace))
              case spec: CumulativeCountWidgetSpec => spec.copy(fieldName = replace(spec.fieldName), groupFieldName = spec.groupFieldName.map(replace))
              case spec: BoxWidgetSpec => spec.copy(fieldName = replace(spec.fieldName), groupFieldName = spec.groupFieldName.map(replace))
              case spec: ScatterWidgetSpec => spec.copy(xFieldName = replace(spec.xFieldName), yFieldName = replace(spec.yFieldName), groupFieldName = spec.groupFieldName.map(replace))
              case spec: ValueScatterWidgetSpec => spec.copy(xFieldName = replace(spec.xFieldName), yFieldName = replace(spec.yFieldName), valueFieldName = replace(spec.valueFieldName))
              case spec: HeatmapAggWidgetSpec => spec.copy(xFieldName = replace(spec.xFieldName), yFieldName = replace(spec.yFieldName), valueFieldName = replace(spec.valueFieldName))
              case spec: GridDistributionCountWidgetSpec => spec.copy(xFieldName = replace(spec.xFieldName), yFieldName = replace(spec.yFieldName))
              case spec: CorrelationWidgetSpec => spec.copy(fieldNames = spec.fieldNames.map(replace))
              case spec: BasicStatsWidgetSpec => spec.copy(fieldName = replace(spec.fieldName))
              case spec: IndependenceTestWidgetSpec => spec.copy(inputFieldNames = spec.inputFieldNames.map(replace), fieldName = replace(spec.fieldName))
              case _ => widgetSpec
            }
          }

          view.copy(tableColumnNames = newTableColumnNames, widgetSpecs = newSpecs)
        }
      }

      _ <- dsa.dataViewRepo.update(newViews)

      // classification runs

      classificationRuns <- dsa.classificationResultRepo.find()

      newClassificationRuns = classificationRuns.map { classificationRun =>
        val newInputFieldNames = classificationRun.setting.inputFieldNames.map(fieldName => oldToNewFieldNameMap.getOrElse(fieldName, fieldName))
        val outputFieldName = classificationRun.setting.outputFieldName
        val newOutputFieldName = oldToNewFieldNameMap.getOrElse(outputFieldName, outputFieldName)

        val newSetting = classificationRun.setting.copy(inputFieldNames = newInputFieldNames, outputFieldName = newOutputFieldName)
        classificationRun.copy(setting = newSetting)
      }

      _ <- dsa.classificationResultRepo.update(newClassificationRuns)

      // regression runs

      regressionRuns <- dsa.regressionResultRepo.find()

      newRegressionRuns = regressionRuns.map { regressionRun =>
        val newInputFieldNames = regressionRun.setting.inputFieldNames.map(fieldName => oldToNewFieldNameMap.getOrElse(fieldName, fieldName))
        val outputFieldName = regressionRun.setting.outputFieldName
        val newOutputFieldName = oldToNewFieldNameMap.getOrElse(outputFieldName, outputFieldName)

        val newSetting = regressionRun.setting.copy(inputFieldNames = newInputFieldNames, outputFieldName = newOutputFieldName)
        regressionRun.copy(setting = newSetting)
      }

      _ <- dsa.regressionResultRepo.update(newRegressionRuns)

    // setting

      setting <- dsa.setting

      newSetting = {
        def replace(fieldName: String) = oldToNewFieldNameMap.getOrElse(fieldName, fieldName)

        setting.copy(
          keyFieldName = replace(setting.keyFieldName),
          exportOrderByFieldName = setting.exportOrderByFieldName.map(replace),
          defaultDistributionFieldName = setting.defaultDistributionFieldName.map(replace),
          defaultCumulativeCountFieldName = setting.defaultCumulativeCountFieldName.map(replace),
          defaultScatterXFieldName = setting.defaultScatterXFieldName.map(replace),
          defaultScatterYFieldName = setting.defaultScatterYFieldName.map(replace)
        )
      }

      _ <- dsa.updateSetting(newSetting)

      // data

      _ <- dsa.updateDataSetRepo

      _ <- dsa.dataSetRepo.deleteAll

      _ <- dsa.dataSetRepo.flushOps

      inputStream <- movedDsa.dataSetRepo.findAsStream()

      _ <- dsa.dataSetRepo.saveAsStream(inputStream, streamSpec)

      _ <- dsa.dataSetRepo.flushOps

      // check the counts

      newCount <- dsa.dataSetRepo.count()

      _ <- Future {
        if (originalCount != newCount)
          throw new AdaException(s"Terminating 'FixNonalphanumericFields' script due to mismatched counts: $originalCount (original) vs. $newCount (new).")
      }

      // moved data cleanup

      dataSpaceId <- movedDsa.metaInfo.map(_.dataSpaceId)

      dataSpace <- dataSpaceMetaInfoRepo.get(dataSpaceId)

      _ <- dataSpaceService.unregister(dataSpace.get, newDataSetId)

      _ <- movedDsa.fieldRepo.deleteAll

      _ <- movedDsa.categoryRepo.deleteAll

      _ <- movedDsa.dataViewRepo.deleteAll

      _ <- movedDsa.filterRepo.deleteAll

      _ <- movedDsa.dataSetRepo.deleteAll
    } yield
      ()
  }

  override def inputType = typeOf[FixNonalphanumericFieldsSpec]
}

case class FixNonalphanumericFieldsSpec(
  dataSetId: String,
  batchSize: Option[Int]
)
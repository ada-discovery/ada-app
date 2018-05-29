package runnables.denopa

import javax.inject.Inject
import dataaccess.RepoTypes.FieldRepo
import dataaccess.{Criterion, CategoryRepo}
import models._
import Criterion.Infix
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import services.{DataSetService, DeNoPaSetting}
import DeNoPaBaselineTranSMARTMapping.{subjectsData, clinicalData}
import DeNoPaBaselineTranSMARTMapping.{fieldCategoryMap => baselineFieldCategoryMap}
import DeNoPaBaselineTranSMARTMapping.{fieldLabelMap => baselineFieldLabelMap}
import runnables.luxpark.DataSetId._
import runnables.GuiceBuilderRunnable
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import dataaccess.CategoryRepo.saveRecursively
import dataaccess.JsonUtil.escapeKey
import scala.concurrent.duration._

import scala.concurrent.Await.result
import scala.concurrent.Future

protected abstract class ImportDeNoPaCategories(
    dataSetId: String,
    coreFieldCategoryMap: Map[String, Category],
    coreFieldLabelMap: Map[String, String],
    fieldNamePrefixReplacement: Option[(String, String)]
  ) extends Runnable {

  @Inject() protected var dataSetService: DataSetService = _
  @Inject() protected var dsaf: DataSetAccessorFactory = _
  private val timeout = 120000 millis

  def run = {
    val dsa = dsaf(dataSetId).get
    val categoryRepo = dsa.categoryRepo
    val fieldRepo = dsa.fieldRepo

    val fieldCategoryMap = fieldNamePrefixReplacement.map { case (from, to) =>
      coreFieldCategoryMap.map { case (fieldName, category) =>
        val newFieldName =
          if (fieldName.startsWith(from)) {
            fieldName.replaceFirst(from, to)
          } else
            fieldName
        (newFieldName, category)
      }
    }.getOrElse(
      coreFieldCategoryMap
    )

    val fieldLabelMap = fieldNamePrefixReplacement.map { case (from, to) =>
      coreFieldLabelMap.map { case (fieldName, label) =>
        val newFieldName =
          if (fieldName.startsWith(from)) {
            fieldName.replaceFirst(from, to)
          } else
            fieldName
        (newFieldName, label)
      }
    }.getOrElse(
      coreFieldLabelMap
    )

    val future = for {
      // delete all the categories
      _ <- categoryRepo.deleteAll

      // save the subject categories
      categoryIds1 <- saveRecursively(categoryRepo, subjectsData)

      // save the clinical categories
      categoryIds2 <- saveRecursively(categoryRepo, clinicalData)

      // get the referenced fields and set the new categories and labels
      newFields: Set[Option[Field]] <- Future.sequence {
        val refFieldNames = (fieldCategoryMap.keys ++ fieldLabelMap.keys).toSet
        val categoryIdMap: Map[Category, BSONObjectID] = (categoryIds1 ++ categoryIds2).toMap

        refFieldNames.map{ fieldName =>
          val escapedFieldName = escapeKey(fieldName)

          fieldRepo.find(Seq("name" #== escapedFieldName)).map {
            _.headOption.map( field =>
              setCategoryAndLabel(field, fieldName, categoryIdMap, fieldCategoryMap, fieldLabelMap)
            )
          }
        }
      }

      // save the new fields
      _ <- fieldRepo.update(newFields.flatten)
    } yield
      ()

    result(future, timeout)
  }

  private def setCategoryAndLabel(
    field: Field,
    fieldName: String,
    categoryIdMap: Map[Category, BSONObjectID],
    fieldCategoryMap: Map[String, Category],
    fieldLabelMap: Map[String, String]
  ): Field = {
    val category = fieldCategoryMap.get(fieldName)
    val label = fieldLabelMap.get(fieldName)

    if (category.isDefined)
      field.categoryId = Some(categoryIdMap(category.get))
    else {
      println(s"No category found for the field $fieldName.")
    }

    val newField = if (label.isDefined)
      field.copy(label = Some(label.get))
    else {
      println(s"No category found for the field $fieldName.")
      field
    }

    newField
  }
}

class ImportDeNoPaRawBaselineCategories extends ImportDeNoPaCategories(
  denopa_raw_clinical_baseline,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  None
)

class ImportDeNoPaRawFirstVisitCategories extends ImportDeNoPaCategories(
  denopa_raw_clinical_first_visit,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  Some(("a_", "b_"))
)

class ImportDeNoPaRawSecondVisitCategories extends ImportDeNoPaCategories(
  denopa_raw_clinical_second_visit,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  Some(("a_", "c_"))
)

class ImportDeNoPaBaselineCategories extends ImportDeNoPaCategories(
  denopa_clinical_baseline,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  None
)

class ImportDeNoPaFirstVisitCategories extends ImportDeNoPaCategories(
  denopa_clinical_first_visit,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  Some(("a_", "b_"))
)

class ImportDeNoPaSecondVisitCategories extends ImportDeNoPaCategories(
  denopa_clinical_second_visit,
  baselineFieldCategoryMap,
  baselineFieldLabelMap,
  Some(("a_", "c_"))
)

// app main launchers
object ImportDeNoPaRawBaselineCategories extends GuiceBuilderRunnable[ImportDeNoPaRawBaselineCategories] with App { run }
object ImportDeNoPaRawFirstVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaRawFirstVisitCategories] with App { run }
object ImportDeNoPaRawSecondVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaRawSecondVisitCategories] with App { run }

object ImportDeNoPaBaselineCategories extends GuiceBuilderRunnable[ImportDeNoPaBaselineCategories] with App { run }
object ImportDeNoPaFirstVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaFirstVisitCategories] with App { run }
object ImportDeNoPaSecondVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaSecondVisitCategories] with App { run }
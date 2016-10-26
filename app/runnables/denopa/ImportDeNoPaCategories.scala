package runnables.denopa

import javax.inject.Inject
import dataaccess.RepoTypes.DictionaryFieldRepo
import dataaccess.{Category, Criterion, Field, DictionaryCategoryRepo}
import Criterion.CriterionInfix
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import services.{DataSetService, DeNoPaSetting}
import DeNoPaTranSMARTMapping._
import runnables.DataSetId._
import runnables.GuiceBuilderRunnable
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import DictionaryCategoryRepo.saveRecursively
import util.JsonUtil.escapeKey
import scala.concurrent.duration._

import scala.concurrent.Await.result
import scala.concurrent.Future

protected abstract class ImportDeNoPaCategories(dataSetId: String) extends Runnable {

  @Inject() protected var dataSetService: DataSetService = _
  @Inject() protected var dsaf: DataSetAccessorFactory = _
  private val timeout = 120000 millis

  def run = {
    val dsa = dsaf(dataSetId).get
    val categoryRepo = dsa.categoryRepo
    val fieldRepo = dsa.fieldRepo

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
              setCategoryAndLabel(field, fieldName, categoryIdMap)
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
    categoryIdMap: Map[Category, BSONObjectID]
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

class ImportDeNoPaBaselineCategories extends ImportDeNoPaCategories(denopa_raw_clinical_baseline)
class ImportDeNoPaFirstVisitCategories extends ImportDeNoPaCategories(denopa_raw_clinical_first_visit)
class ImportDeNoPaSecondVisitCategories extends ImportDeNoPaCategories(denopa_raw_clinical_second_visit)
class ImportDeNoPaCuratedBaselineCategories extends ImportDeNoPaCategories(denopa_clinical_baseline)
class ImportDeNoPaCuratedFirstVisitCategories extends ImportDeNoPaCategories(denopa_clinical_first_visit)
class ImportDeNoPaCuratedSecondVisitCategories extends ImportDeNoPaCategories(denopa_clinical_second_visit)

// app main launchers
object ImportDeNoPaBaselineCategories extends GuiceBuilderRunnable[ImportDeNoPaBaselineCategories] with App { run }
object ImportDeNoPaFirstVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaFirstVisitCategories] with App { run }
object ImportDeNoPaSecondVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaSecondVisitCategories] with App { run }

object ImportDeNoPaCuratedBaselineCategories extends GuiceBuilderRunnable[ImportDeNoPaCuratedBaselineCategories] with App { run }
object ImportDeNoPaCuratedFirstVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaCuratedFirstVisitCategories] with App { run }
object ImportDeNoPaCuratedSecondVisitCategories extends GuiceBuilderRunnable[ImportDeNoPaCuratedSecondVisitCategories] with App { run }
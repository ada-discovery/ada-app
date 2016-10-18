package runnables.denopa

import javax.inject.Inject
import dataaccess.{Category, Criterion, DictionaryCategoryRepo}
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

    // delete all categories
    result(categoryRepo.deleteAll, timeout)

    val categoryIdsFuture1 = saveRecursively(categoryRepo, subjectsData)
    val categoryIdsFuture2 = saveRecursively(categoryRepo, clinicalData)

    val categoryIdMap: Map[Category, BSONObjectID] =
      (result(categoryIdsFuture1, timeout) ++ result(categoryIdsFuture2, timeout)).toMap

    val refFieldNames = (fieldCategoryMap.keys ++ fieldLabelMap.keys).toSet

    val updateFieldFutures = refFieldNames.map{ fieldName =>
      val escapedFieldName = escapeKey(fieldName)

      fieldRepo.find(Seq("name" #== escapedFieldName)).map { fields =>
        if (fields.nonEmpty) {
          val field = fields.head

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

          fieldRepo.update(newField)
        } else {
          println(s"$fieldName not found.")
          Future(())
        }
      }
    }

    result(Future.sequence(updateFieldFutures), timeout)
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
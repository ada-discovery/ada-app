package runnables.denopa

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import services.DeNoPaSetting
import DeNoPaTranSMARTMapping._
import models.Category
import models.DataSetId._
import runnables.{InferDictionary, GuiceBuilderRunnable}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import persistence.dataset.DictionaryCategoryRepo.saveRecursively
import util.JsonUtil.escapeKey

import scala.concurrent.Await.result
import scala.concurrent.Future

protected abstract class InferDeNoPaDictionary(dataSetId: String) extends InferDictionary(dataSetId) {
  override protected val typeInferenceProvider = DeNoPaSetting.typeInferenceProvider
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> "1")

  override def run = {
    result(categoryRepo.initIfNeeded, timeout)
    result(categoryRepo.deleteAll, timeout)

    val categoryIdsFuture1 = saveRecursively(categoryRepo, subjectsData)
    val categoryIdsFuture2 = saveRecursively(categoryRepo, clinicalData)

    val categoryIdMap: Map[Category, BSONObjectID] =
      (result(categoryIdsFuture1, timeout) ++ result(categoryIdsFuture2, timeout)).toMap

    super.run

    val fieldNames = (fieldCategoryMap.keys ++ fieldLabelMap.keys).toSet

    val updateFieldFutures = fieldNames.map{ fieldName =>
      val escapedFieldName = escapeKey(fieldName)

      fieldRepo.find(Some(Json.obj("name" -> escapedFieldName))).map { fields =>
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

class InferDeNoPaBaselineDictionary extends InferDeNoPaDictionary(denopa_baseline)
class InferDeNoPaFirstVisitDictionary extends InferDeNoPaDictionary(denopa_firstvisit)
class InferDeNoPaCuratedBaselineDictionary extends InferDeNoPaDictionary(denopa_curated_baseline) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}
class InferDeNoPaCuratedFirstVisitDictionary extends InferDeNoPaDictionary(denopa_curated_firstvisit) {
  override protected val uniqueCriteria = Json.obj("Line_Nr" -> 1)
}

// app main launchers
object InferDeNoPaBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaBaselineDictionary] with App { run }
object InferDeNoPaFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaFirstVisitDictionary] with App { run }
object InferDeNoPaCuratedBaselineDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedBaselineDictionary] with App { run }
object InferDeNoPaCuratedFirstVisitDictionary extends GuiceBuilderRunnable[InferDeNoPaCuratedFirstVisitDictionary] with App { run }

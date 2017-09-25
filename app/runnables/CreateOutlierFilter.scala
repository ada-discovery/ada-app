package runnables
import javax.inject.Inject

import dataaccess.{AsyncReadonlyRepo, Criterion, EqualsCriterion, NotEqualsNullCriterion}
import models.DataSetFormattersAndIds.FieldIdentity
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import services.StatsService
import dataaccess.Criterion._
import models.{ConditionType, Field, Filter, FilterCondition}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateOutlierFilter @Inject() (
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends FutureRunnable {

  private val dataSetIds =
    (7 to 61).map (index =>
      "mpower_challenge.walking_activity_training_norms_rc_weights_" + index
    )

  private val conditionCriterionNames = Seq(
    (
      "Diagnosis Not Null (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.NotEquals, "", None),
      NotEqualsNullCriterion("professional-diagnosis")
    ),
    (
      "Diagnosis True (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.Equals, "true", None),
      EqualsCriterion("professional-diagnosis", true)
    ),
    (
      "Diagnosis False (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.Equals, "false", None),
      EqualsCriterion("professional-diagnosis", false)
    )
  )

  override def runAsFuture =
    for {
      _<- util.seqFutures(dataSetIds) { dataSetId =>
        val dsa = dsaf(dataSetId).get
        Future.sequence(
          conditionCriterionNames.map { case (name, condition, criterion) =>
            createFilter(dsa, name, Seq(criterion), Seq(condition))
          }
        )
      }
    } yield
      ()

  private def createFilter(
    dsa: DataSetAccessor,
    name: String,
    criteria: Seq[Criterion[Any]],
    conditionsToInclude: Seq[FilterCondition]
  ) =
    for {
    // get all the fields
      allFields <- dsa.fieldRepo.find()

      // filter the weight fields
      weightsFields = allFields.filter(_.name.startsWith("rc_w_"))

      // create non-outlier conditions
      conditions <- nonOutlierConditions(dsa.dataSetRepo, weightsFields, criteria)

      // save a new filter
      _ <- dsa.filterRepo.save(
        Filter(None, Some(name), conditions.toSeq ++ conditionsToInclude)
      )
    } yield
      println(s"Filter $name created for ${dsa.dataSetId}.")

  private def nonOutlierConditions(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    fields: Traversable[Field],
    criteria: Seq[Criterion[Any]]
  ): Future[Traversable[FilterCondition]] =
    for {
//      // get the fields
//      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))

      // calc the quantiles
      fieldQuantiles <- Future.sequence(
        fields.map { field =>
          statsService.calcQuantiles(dataRepo, criteria, field).map(
            _.map( quantiles =>
            (field, quantiles)
          ))
        }
      )
    } yield
      fieldQuantiles.flatten.flatMap { case (field, quantiles) =>
        Seq(
          FilterCondition(field.name, None, ConditionType.GreaterEqual, quantiles.lowerWhisker.toString, None),
          FilterCondition(field.name, None, ConditionType.LessEqual, quantiles.upperWhisker.toString, None)
        )
      }
}

object CreateOutlierFilter extends GuiceBuilderRunnable[CreateOutlierFilter] with App { run }
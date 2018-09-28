package runnables.mpower

import javax.inject.Inject

import models.{Field, Filter}
import org.incal.core.{ConditionType, FilterCondition}
import org.incal.core.dataaccess.{AsyncReadonlyRepo, Criterion, EqualsCriterion, NotEqualsNullCriterion}
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import services.stats.StatsService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe._

class CreateWeightOutlierDiagnosisFilter @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CreateWeightOutlierDiagnosisFilterSpec] {

  private val conditionCriterionNames = Seq(
    (
      "Diagnosis Not Null (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.NotEquals, None, None),
      NotEqualsNullCriterion("professional-diagnosis")
    ),
    (
      "Diagnosis True (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.Equals, Some("true"), None),
      EqualsCriterion("professional-diagnosis", true)
    ),
    (
      "Diagnosis False (RC_W wo Outliers)",
      FilterCondition("professional-diagnosis", None, ConditionType.Equals, Some("false"), None),
      EqualsCriterion("professional-diagnosis", false)
    )
  )

  override def runAsFuture(spec: CreateWeightOutlierDiagnosisFilterSpec) = {
    val dataSetIds =
      (spec.suffixFrom, spec.suffixTo).zipped.headOption.map { case (from, to) =>
        (from to to).map(spec.dataSetId + _)
      }.getOrElse(
        Seq(spec.dataSetId)
      )

    for {
      _ <- util.seqFutures(dataSetIds) { dataSetId =>
        val dsa = dsaf(dataSetId).get
        Future.sequence(
          conditionCriterionNames.map { case (name, condition, criterion) =>
            createFilter(dsa, name, Seq(criterion), Seq(condition))
          }
        )
      }
    } yield
      ()
  }

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
      // calc the quantiles
      fieldQuantiles <- Future.sequence(
        fields.map { field =>
          statsService.calcQuartilesFromRepo(dataRepo, criteria, field).map(
            _.map( quantiles =>
            (field, quantiles)
          ))
        }
      )
    } yield
      fieldQuantiles.flatten.flatMap { case (field, quantiles) =>
        Seq(
          FilterCondition(field.name, None, ConditionType.GreaterEqual, Some(quantiles.lowerWhisker.toString), None),
          FilterCondition(field.name, None, ConditionType.LessEqual, Some(quantiles.upperWhisker.toString), None)
        )
      }

  override def inputType = typeOf[CreateWeightOutlierDiagnosisFilterSpec]
}

case class CreateWeightOutlierDiagnosisFilterSpec(dataSetId: String, suffixFrom: Option[Int], suffixTo: Option[Int])
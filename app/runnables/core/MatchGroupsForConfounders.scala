package runnables.core

import javax.inject.Inject

import org.ada.server.models.DerivedDataSetSpec
import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.FilterCondition.toCriteria
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import org.ada.server.services.DataSetService
import org.ada.server.field.FieldUtil.valueConverters

import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class MatchGroupsForConfounders @Inject() (
  dsaf: DataSetAccessorFactory,
  dataSetService: DataSetService
) extends InputFutureRunnable[MatchGroupsForConfoundersSpec] {

  override def runAsFuture(
    input: MatchGroupsForConfoundersSpec
  ): Future[Unit] = {
    val dsa  = dsaf(input.dataSetId).get

    val ratios = input.targetGroupSelectRatios match {
      case Nil => Stream.continually(1)
      case _ => input.targetGroupSelectRatios
    }

    for {
      // load a filter (if needed)
      filter <- input.filterId.map(dsa.filterRepo.get).getOrElse(Future(None))

      // create criteria
      criteria <- filter.map { filter =>
        val fieldNames = filter.conditions.map(_.fieldName)
        valueConverters(dsa.fieldRepo, fieldNames).map(toCriteria(_, filter.conditions))
      }.getOrElse(Future(Nil))

      // match groups
      _ <- dataSetService.matchGroups(
        input.dataSetId,
        input.derivedDataSetSpec,
        criteria,
        input.targetGroupFieldName,
        input.confoundingFieldNames,
        input.numericDistTolerance,
        input.targetGroupDisplayStrings.zip(ratios)
      )
    } yield
      ()
  }

  override def inputType = typeOf[MatchGroupsForConfoundersSpec]
}

case class MatchGroupsForConfoundersSpec(
  dataSetId: String,
  derivedDataSetSpec: DerivedDataSetSpec,
  filterId: Option[BSONObjectID],
  targetGroupFieldName: String,
  confoundingFieldNames: Seq[String],
  numericDistTolerance: Double,
  targetGroupDisplayStrings: Seq[String],
  targetGroupSelectRatios: Seq[Int]
)
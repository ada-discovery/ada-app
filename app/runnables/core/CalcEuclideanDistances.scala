package runnables.core

import java.{util => ju}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.Inject
import dataaccess.{AsyncReadonlyRepo, Criterion}
import dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import models.{Field, FieldTypeId}
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.stats.StatsService
import util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CalcEuclideanDistances @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcEuclideanDistancesSpec] {

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  import statsService._

  private val logger = Logger

  def runAsFuture(input: CalcEuclideanDistancesSpec) = {
    val dsa = dsaf(input.dataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    for {
      // get the fields first
      numericFields <-
        if (input.featuresNum.isDefined)
          dsa.fieldRepo.find(
            Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date)),
            limit = Some(input.featuresNum.get)
          )
        else
          dsa.fieldRepo.find(
            Seq(
              "fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer, FieldTypeId.Date),
              FieldIdentity.name #!-> input.allFeaturesExcept
            )
          )

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      (euclideanDistances, execTime) <- {
        val calcStart = new ju.Date
        seqFutures(1 to input.standardRepetitions) { _ =>
          for {
            items <- dataSetRepo.find(projection = fieldNames)
          } yield
            euclideanDistanceExec.execJson((), sortedFields)(items)
        }.map { results =>
          if (results.isEmpty) {
            (Nil, 0)
          } else {
            val execTime = new ju.Date().getTime - calcStart.getTime
            (results.head, execTime.toDouble / (1000 * input.standardRepetitions))
          }
        }
      }

      (streamedEuclideanDistances, streamExecTime) <- {
        val calcStart = new ju.Date
        seqFutures(1 to input.streamRepetitions) { _ =>
          calcEuclideanDistanceStreamed(dataSetRepo, Nil, sortedFields, input.streamParallelism, input.streamWithProjection, input.streamAreValuesAllDefined)
        }.map { results =>
          val execTime = new ju.Date().getTime - calcStart.getTime
          (results.head, execTime.toDouble / (1000 * input.streamRepetitions))
        }
      }
    } yield {
      logger.info(s"Euclidean distances for ${numericFields.size} fields using ALL DATA finished in ${execTime} sec on average.")
      logger.info(s"Euclidean distances for ${numericFields.size} fields using STREAMS finished in ${streamExecTime} sec on average.")

      euclideanDistances.zip(streamedEuclideanDistances).map { case (rowCor1, rowCor2) =>
        rowCor1.zip(rowCor2).map { case (cor1, cor2) =>
          assert(cor1.equals(cor2), s"$cor1 is not equal $cor2.")
        }
      }

      val euclideanDistancesToExport = if (euclideanDistances.nonEmpty) euclideanDistances else streamedEuclideanDistances
      input.exportFileName.map { exportFileName =>
        logger.info(s"Exporting the calculated Euclidean distances to $exportFileName.")
        FeatureMatrixIO.saveSquare(
          euclideanDistancesToExport,
          sortedFields.map(_.name),
          exportFileName,
          (value: Double) => value.toString
        )
      }.getOrElse(
        ()
      )
    }
  }

  private def calcEuclideanDistanceStreamed(
    dataRepo: AsyncReadonlyRepo[JsObject, BSONObjectID],
    criteria: Seq[Criterion[Any]],
    fields: Seq[Field],
    parallelism: Option[Int] = None,
    withProjection: Boolean = true,
    areValuesAllDefined: Boolean = false
  ): Future[Seq[Seq[Double]]] = {
    val exec =
      if (areValuesAllDefined)
        (euclideanDistanceAllDefinedExec.execJsonRepoStreamed)_
      else
        (euclideanDistanceExec.execJsonRepoStreamed)_

    exec(parallelism, (), withProjection, fields)(dataRepo, criteria)
  }

  override def inputType = typeOf[CalcEuclideanDistancesSpec]
}

case class CalcEuclideanDistancesSpec(
  dataSetId: String,
  featuresNum: Option[Int],
  allFeaturesExcept: Seq[String],
  standardRepetitions: Int,
  streamRepetitions: Int,
  streamParallelism: Option[Int],
  streamWithProjection: Boolean,
  streamAreValuesAllDefined: Boolean,
  exportFileName: Option[String]
)
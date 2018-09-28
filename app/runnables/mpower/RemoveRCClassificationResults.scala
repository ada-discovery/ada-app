package runnables.mpower

import javax.inject.Inject

import dataaccess.ClassificationResultRepoFactory
import models.AdaException
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import org.incal.core.InputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class RemoveRCClassificationResults @Inject()(
    dsaf: DataSetAccessorFactory,
    classificationResultRepoFactory: ClassificationResultRepoFactory
  ) extends InputFutureRunnable[RemoveRCClassificationResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  override def runAsFuture(input: RemoveRCClassificationResultsSpec) = {
    val resultsDsa = dsaf(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    for {
      // get the data set ids
      jsons <- resultsDsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // remove all the classification results
      _ <- util.seqFutures(dataSetIds.grouped(input.groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          logger.info(s"Removing classification results in $id...")
          classificationResultRepoFactory(id).deleteAll
        })
      }
    } yield
      ()
  }

  override def inputType = typeOf[RemoveRCClassificationResultsSpec]
}

case class RemoveRCClassificationResultsSpec(dataSetId: String, groupSize: Int)
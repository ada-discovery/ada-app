package runnables.mpower

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.{ClassificationResultRepoFactory, DataSetAccessorFactory}
import play.api.Logger
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class RemoveRCClassificationResults @Inject()(
    dsaf: DataSetAccessorFactory,
    classificationResultRepoFactory: ClassificationResultRepoFactory
  ) extends InputFutureRunnableExt[RemoveRCClassificationResultsSpec] {

  private val logger = Logger // (this.getClass())

  private val dataSetFieldName = "inputOutputSpec-resultDataSetId"

  override def runAsFuture(input: RemoveRCClassificationResultsSpec) = {
    val resultsDsa = dsaf.applySync(input.dataSetId).getOrElse(
      throw new AdaException(s"Data set ${input.dataSetId} not found.")
    )

    for {
      // get the data set ids
      jsons <- resultsDsa.dataSetRepo.find(projection = Seq(dataSetFieldName))
      dataSetIds = jsons.map { json => (json \ dataSetFieldName).as[String] }.toSeq.sorted

      // remove all the classification results
      _ <- seqFutures(dataSetIds.grouped(input.groupSize)) { ids =>
        Future.sequence(ids.map { id =>
          logger.info(s"Removing classification results in $id...")
          classificationResultRepoFactory(id).deleteAll
        })
      }
    } yield
      ()
  }
}

case class RemoveRCClassificationResultsSpec(dataSetId: String, groupSize: Int)
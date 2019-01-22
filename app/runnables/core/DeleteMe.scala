package runnables.core

import javax.inject.Inject

import models.DataSetFormattersAndIds.CategoryIdentity
import org.incal.core.dataaccess.Criterion._
import org.incal.core.{FutureRunnable, InputFutureRunnable}
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

class DeleteMe @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dataSetId = "trend.clinical_visit"
  private val refCategoryIds = Seq(
    BSONObjectID.parse("5b87e51be40000eb01a3bea5").get,
    BSONObjectID.parse("5b87e51be40000f801a3beb2").get,
    BSONObjectID.parse("5b87e51be40000f801a3bec0").get
  )

  override def runAsFuture: Future[Unit] = {
    val dsa = dsaf(dataSetId).get

    for {
      lala <- dsa.categoryRepo.find(Seq(CategoryIdentity.name #-> refCategoryIds))
    } yield
      println(lala)
  }
}

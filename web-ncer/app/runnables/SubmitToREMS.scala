package runnables

import com.google.inject.Inject
import org.incal.core.FilterCondition
import org.incal.core.runnables.FutureRunnable
import services.SampleRequestService

import scala.concurrent.ExecutionContext.Implicits.global


class SubmitToREMS @Inject() (
  sampleRequestService: SampleRequestService
) extends FutureRunnable {
  // TODO: These will be parameters
  private val dataSetId = "open.iris"
  private val filter: Seq[FilterCondition] = Nil
  private val tableColumNames = Vector("petal_length", "petal_width")

  override def runAsFuture = {
    for {
      csv <- sampleRequestService.createCsv(dataSetId, filter, tableColumNames)
    } yield {
      sampleRequestService.sendToRems(csv)
    }
  }

}


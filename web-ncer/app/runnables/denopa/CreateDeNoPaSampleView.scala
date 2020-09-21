package runnables.denopa

import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.ada.server.models._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class CreateDeNoPaSampleView extends DsaInputFutureRunnable[CreateDeNoPaSampleViewSpec] {

  private val logger = Logger // (this.getClass())

  private val columnNames = Seq("Subject_ID","Visit","Visit_ID","IBBL_ID","Box_ID","Box_Position")

  override def runAsFuture(input: CreateDeNoPaSampleViewSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // create and save the main view
      _ <- dsa.dataViewRepo.save(createView)
    } yield
      ()

  private def createView: DataView = {

    val distributionWidgets = Seq(
      DistributionWidgetSpec("Box_ID", None, displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Pie)
      )),
      DistributionWidgetSpec("Visit", None, displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Bar)
      ))
    )

    DataView(
      None, "Sample", Nil,
      columnNames,
      distributionWidgets,
      4,
      false,
      false
    )
  }
}

case class CreateDeNoPaSampleViewSpec(dataSetId: String)
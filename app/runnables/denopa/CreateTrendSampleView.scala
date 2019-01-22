package runnables.denopa

import play.api.Logger
import runnables.DsaInputFutureRunnable
import models._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class CreateTrendSampleView extends DsaInputFutureRunnable[CreateTrendSampleViewSpec] {

  private val logger = Logger // (this.getClass())

  private val columnNames = Seq("Hertie_ID","Trend_ID","Datum","Tube_ID","Status","Material","IBBL_ID","Box_ID","Box_Position")

  override def runAsFuture(input: CreateTrendSampleViewSpec) = {
    val viewRepo = createDsa(input.dataSetId).dataViewRepo

    for {
      // create and save the main view
      _ <- viewRepo.save(createView)
    } yield
      ()
  }

  private def createView: DataView = {

    val distributionWidgets = Seq(
      DistributionWidgetSpec("Box_ID", None, displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Pie)
      )),
      DistributionWidgetSpec("Status", None, displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Bar)
      )),
      DistributionWidgetSpec("Datum", None, displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Column)
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

  override def inputType = typeOf[CreateTrendSampleViewSpec]
}

case class CreateTrendSampleViewSpec(dataSetId: String)
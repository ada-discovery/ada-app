package runnables.mpower

import javax.inject.Inject
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.JsObject
import org.ada.server.services.DataSetService
import org.ada.server.dataaccess.JsonUtil
import org.incal.core.{PlotSetting, PlotlyPlotter}
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.util.writeStringAsStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class ExtractSeriesPeaks @Inject() (
    dss: DataSetService,
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnableExt[ExtractSeriesPeaksSpec] {

  private val dataSetId = "lux_park.mpower_walking_activity"
//  private val seriesFieldName = "accel_walking_outboundu002ejsonu002eitems.x"
  private val seriesFieldName = "deviceMotion_walking_outboundu002ejsonu002eitems.gravity.x"
  private val recordId = "602681c6-fb35-4513-be00-4992ad00c215"

  // helper method to extract series
  def extractSeries(json: JsObject): Seq[Double] = {
    val jsValues = JsonUtil.traverse(json, seriesFieldName)
    jsValues.map(_.as[Double])
  }

  override def runAsFuture(spec: ExtractSeriesPeaksSpec) = {
    for {
      // data set accessor
      dsa <- dsaf.getOrError(dataSetId)

      // retrieve jsons for a given record id
      json <- dsa.dataSetRepo.find(
        criteria = Seq("recordId" #== recordId),
        projection = Seq(seriesFieldName.split('.')(0)),
        limit = Some(1)
      ).map(_.head)
    } yield {
      val series = extractSeries(json)
      val newSeries = dss.extractPeaks(series, spec.peakNum, spec.peakSelectionRatio)
      // plot series
      plot(series, "Original", "walking-original.svg")
      plot(newSeries.get, "Extracted", "walking-extracted.svg")
    }
  }

  private def plot(
    series: Seq[Double],
    title: String,
    fileName: String
  ) =
    PlotlyPlotter.plotLines(
      data = Seq(series),
      setting = PlotSetting(title = Some(title)),
      outputFileName = fileName
    )
}

case class ExtractSeriesPeaksSpec(peakNum: Int, peakSelectionRatio: Option[Double])
package services.ml

import org.ada.server.models.ml.IOJsonTimeSeriesSpec
import org.ada.server.dataaccess.JsonUtil
import play.api.libs.json.JsObject

object IOSeriesUtil {

  def apply(
    json: JsObject,
    ioSpec: IOJsonTimeSeriesSpec
  ): Option[(Seq[Seq[Double]], Seq[Double])] = {
    // helper method to extract series for a given path
    def extractSeries(path: String): Option[Seq[Double]] = {
      val jsValues = JsonUtil.traverse(json, path)

      val leftTrimmedJsValues =
        ioSpec.dropLeftLength.map(jsValues.drop).getOrElse(jsValues)

      val trimmedJsValues = ioSpec.seriesLength match {
        case Some(length) =>
          val series = leftTrimmedJsValues.take(length)
          if (series.size == length) Some(series) else None

        case None =>
          val series = ioSpec.dropRightLength.map(leftTrimmedJsValues.dropRight).getOrElse(leftTrimmedJsValues)
          Some(series)
      }

      trimmedJsValues.map(_.map(_.as[Double]))
    }

    // extract input series
    val inputSeriesAux = ioSpec.inputSeriesFieldPaths.flatMap(extractSeries)

    val inputSeries =
      if (inputSeriesAux.size == ioSpec.inputSeriesFieldPaths.size)
        Some(inputSeriesAux.transpose)
      else
        None

    // extract output series
    val outputSeries = extractSeries(ioSpec.outputSeriesFieldPath)

    (inputSeries, outputSeries).zipped.headOption
  }
}

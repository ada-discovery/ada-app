package services.ml

import dataaccess.JsonUtil
import play.api.libs.json.JsObject

object IOSeriesUtil {

  def applyJson(
    json: JsObject,
    inputSeriesFieldPaths: Seq[String],
    outputSeriesFieldPath: String,
    dropLeftLength: Option[Int] = None,
    dropRightLength: Option[Int] = None,
    seriesLength: Option[Int] = None
  ): Option[(Seq[Seq[Double]], Seq[Double])] = {
    // helper method to extract series for a given path
    def extractSeries(path: String): Option[Seq[Double]] = {
      val jsValues = JsonUtil.traverse(json, path)

      val leftTrimmedJsValues =
        dropLeftLength.map(jsValues.drop).getOrElse(jsValues)

      val trimmedJsValues = seriesLength match {
        case Some(length) =>
          val series = leftTrimmedJsValues.take(length)
          if (series.size == length) Some(series) else None

        case None =>
          val series = dropRightLength.map(leftTrimmedJsValues.dropRight).getOrElse(leftTrimmedJsValues)
          Some(series)
      }

      trimmedJsValues.map(_.map(_.as[Double]))
    }

    // extract input series
    val inputSeriesAux = inputSeriesFieldPaths.flatMap(extractSeries)

    val inputSeries =
      if (inputSeriesAux.size == inputSeriesFieldPaths.size)
        Some(inputSeriesAux.transpose)
      else
        None

    // extract output series
    val outputSeries = extractSeries(outputSeriesFieldPath)

    (inputSeries, outputSeries).zipped.headOption
  }
}

package runnables.core

import com.google.inject.Inject
import dataaccess.FieldTypeHelper
import models.AdaException
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import runnables.InputFutureRunnable
import runnables.core.CalcUtil._
import services.stats.{StatsService, TSNESetting}
import services.stats.calc.JsonFieldUtil._
import smile.plot._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CalcTSNEProjectionForRows @Inject()(
    dsaf: DataSetAccessorFactory,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcTSNEProjectionForRowsSpec] {

  import statsService._

  private val logger = Logger
  implicit val ftf = FieldTypeHelper.fieldTypeFactory()

  def runAsFuture(input: CalcTSNEProjectionForRowsSpec) = {
    val dsa = dsaf(input.dataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    for {
      // get the fields first
      numericFields <- numericFields(dsa.fieldRepo)(input.featuresNum, input.allFeaturesExcept)

      // id label field
      idLabelField <- dsa.fieldRepo.get(input.idLabelFieldName).map(
        _.getOrElse(throw new AdaException(s"Field ${input.idLabelFieldName} not found"))
      )

      // sorted fields
      sortedFields = numericFields.toSeq.sortBy(_.name)
      fieldNames = sortedFields.map(_.name)

      // run t-SNE and obtain the results and the exec time (in sec)
      ((tsneProjections, idLabels), execTime) <- repeatWithTime(input.repetitions) {
        dataSetRepo.find(projection = fieldNames ++ Seq(input.idLabelFieldName)).map { jsons =>
          val jsonConverter = jsonToArrayDoublesDefined(sortedFields)
          val inputs = jsons.map(jsonConverter)

          val idLabelJsonConverter = jsonToDisplayString(idLabelField)
          val idLabels = jsons.map(idLabelJsonConverter)

          // prepare the setting
          val setting = TSNESetting(
            dim = input.dims,
            iterations = input.iterations.getOrElse(1000),
            perplexity = input.perplexity.getOrElse(20),
            eta = input.eta.getOrElse(200)
          )

          // run t-SNE
          val results = performTSNE(inputs.toArray, setting)
          (results, idLabels)
        }
      }
    } yield {
      logger.info(s"Row-based t-SNE for ${numericFields.size} fields finished in ${execTime} sec on average.")

      if (input.plotExportFileName.isDefined) {
        val tsneFailed = tsneProjections.exists(_.exists(_.isNaN))
        if (tsneFailed)
          logger.error(s"Row-based t-SNE for ${numericFields.size} fields return NaN values. Image export not possible.")
        else {
          val labels = tsneProjections.map(_ => 0)
          val canvas = ScatterPlot.plot(tsneProjections, labels, 'o', Palette.COLORS)
          saveCanvasAsImage(input.plotExportFileName.get, 1000, 800)(canvas)
        }
      }

      input.exportFileName.map { exportFileName =>
        logger.info(s"Exporting the calculated row-based t-SNE projections to $exportFileName.")
        FeatureMatrixIO.save(
          tsneProjections.map(_.toSeq),
          idLabels.toSeq.map(_.getOrElse("")),
          for (i <- 1 to input.dims) yield "x" + i,
          input.idLabelFieldName,
          exportFileName,
          (value: Double) => value.toString
        )
      }.getOrElse(
        ()
      )
    }
  }

  override def inputType = typeOf[CalcTSNEProjectionForRowsSpec]
}

case class CalcTSNEProjectionForRowsSpec(
  dataSetId: String,
  featuresNum: Option[Int],
  allFeaturesExcept: Seq[String],
  idLabelFieldName: String,
  dims: Int,
  iterations: Option[Int],
  perplexity: Option[Double],
  eta: Option[Double],
  repetitions: Int,
  exportFileName: Option[String],
  plotExportFileName: Option[String]
)
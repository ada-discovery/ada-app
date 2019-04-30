package controllers.ml

import java.{lang => jl}

import javax.inject.Inject
import com.banda.math.business.rand.RandomDistributionProviderFactory
import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.domain.ActivationFunctionType
import org.ada.server.models.{ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec, RCPredictionSettings}
import org.ada.server.models.ml._
import org.incal.spark_ml.models.VectorScalerType
import org.ada.server.dataaccess.RepoTypes.MessageRepo
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.{Configuration, Logger}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import org.incal.play.controllers._
import org.incal.play.formatters._
import org.incal.play.security.SecurityUtil.restrictAdminAnyNoCaching
import org.incal.core.util.seqFutures
import services.DataSpaceService
import services.ml.RCPredictionService
import util.MessageLogger

import scala.concurrent.ExecutionContext.Implicits.global

@Deprecated
class RCPredictionController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    mPowerWalkingRCPredictionService: RCPredictionService,
    messageRepo: MessageRepo
  ) extends BaseController {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  private implicit val stringSeqFormatter = SeqFormatter.apply
  private implicit val intSeqFormatter = SeqFormatter.applyInt
  private implicit val doubleSeqFormatter = SeqFormatter.applyDouble
  private implicit val vectorScalerTypeFormatter = EnumFormatter(VectorScalerType)
  private implicit val activationFunctionTypeFormatter = JavaEnumFormatter[ActivationFunctionType]

  private val rcPredictionSettingsForm = Form(
    mapping(
      "reservoirNodeNums" -> of[Seq[Int]],
      "inputReservoirConnectivities" -> of[Seq[Double]],
      "reservoirSpectralRadiuses" -> of[Seq[Double]],
      "inScales" -> of[Seq[Double]],
      "predictAheads" -> of[Seq[Int]],
      "reservoirInDegrees" -> optional(of[Seq[Int]]),
      "reservoirCircularInEdges" -> optional(of[Seq[Int]]),
      "reservoirFunctionType" -> of[ActivationFunctionType],
      "reservoirFunctionParams" -> optional(of[Seq[Double]]),
      "seriesPreprocessingType" -> optional(of[VectorScalerType.Value]),
      "inputSeriesFieldPaths" -> of[Seq[String]],
      "outputSeriesFieldPaths" -> of[Seq[String]],
      "washoutPeriod" -> number(min = 0),
      "dropLeftLength" -> optional(number(min = 0)),
      "dropRightLength" -> optional(number(min = 0)),
      "seriesLength" -> optional(number(min = 0)),
      "sourceDataSetId" -> nonEmptyText,
      "resultDataSetId" -> nonEmptyText,
      "resultDataSetName" -> nonEmptyText,
      "resultDataSetIndex" -> optional(number(min = 0, max = 2000)),
      "batchSize" -> optional(number(min = 1, max = 200)),
      "preserveWeightFieldNames" -> of[Seq[String]]
    )(RCPredictionSettings.apply)(RCPredictionSettings.unapply))

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    reservoirInDegree: Option[Int],
    reservoirCircularInEdges: Option[Seq[Int]],
    reservoirFunctionType: ActivationFunctionType,
    reservoirFunctionParams: Option[Seq[Double]],
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double,
    inScale: Double,
    washoutPeriod: Int,
    _predictAhead: Int,
    _seriesPreprocessingType: Option[VectorScalerType.Value],
    weightRd: RandomDistribution[jl.Double]
  ) = new ExtendedReservoirLearningSetting {
    setWeightAdaptationIterationNum(2)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(inScale)
    setOutScale(1d)
    setBias(1d)
    setNonBiasInitial(0d)
    setReservoirNodeNum(reservoirNodeNum)
    setReservoirInDegree(reservoirInDegree)
    setReservoirCircularInEdges(reservoirCircularInEdges)
    setReservoirInDegreeDistribution(None) // Some(RandomDistribution.createPositiveNormalDistribution(classOf[Integer], 50d, 0d))
    setReservoirEdgesNum(None) // Some((0.02 * (250 * 250)).toInt)
    setReservoirPreferentialAttachment(false)
    setReservoirBias(false)
    setInputReservoirConnectivity(inputReservoirConnectivity)
    setReservoirSpectralRadius(reservoirSpectralRadius)
    setReservoirFunctionType(reservoirFunctionType)
    setReservoirFunctionParams(reservoirFunctionParams.map(_.map(x => x: jl.Double))) // Some(Seq(0.5d : jl.Double, 0.25 * math.Pi : jl.Double, 0d : jl.Double))
    setWeightDistribution(weightRd)
    setWashoutPeriod(washoutPeriod)
    predictAhead = _predictAhead
    seriesPreprocessingType = _seriesPreprocessingType
  }

  def showRCPrediction = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      for {
        tree <- dataSpaceService.getTreeForCurrentUser(request)
      } yield
        Ok(views.html.admin.rcPrediction(rcPredictionSettingsForm, tree))
  }

  def runRCPrediction = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>
      rcPredictionSettingsForm.bindFromRequest.fold(
        { formWithErrors =>
          for {
            tree <- dataSpaceService.getTreeForCurrentUser(request)
          } yield {
            BadRequest(views.html.admin.rcPrediction(formWithErrors, tree))
          }
        },
        settings =>
          for {
            _ <- seqFutures(toRCSettings(settings))(
              (mPowerWalkingRCPredictionService.predictAndStoreResults(_,_,_,_)).tupled
            )
          } yield {
            Ok("Hooray")
          }
      )
  }

  private def toRCSettings(
    settings: RCPredictionSettings
  ): Seq[(ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec, Option[Int], Seq[String])] = {

    // get the maximum size of the param seqs
    val maxSize = Seq(
      settings.reservoirNodeNums,
      settings.reservoirInDegrees.getOrElse(Nil),
      settings.inputReservoirConnectivities,
      settings.reservoirSpectralRadiuses,
      settings.inScales,
      settings.predictAheads
    ).foldLeft(0) { case (minSize, seq) => Math.max(minSize, seq.size) }

    def stream[T](xs: Seq[T]) = Stream.continually(xs).flatten.take(maxSize)

    val reservoirInDegreesInit: Seq[Option[Int]] = settings.reservoirInDegrees.map(_.map(Some(_))).getOrElse(Seq(None))

    stream(settings.reservoirNodeNums).zip(
    stream(reservoirInDegreesInit).zip(
    stream(settings.inputReservoirConnectivities).zip(
    stream(settings.reservoirSpectralRadiuses).zip(
    stream(settings.inScales).zip(
    stream(settings.predictAheads)))))).zipWithIndex.map {

      case ((reservoirNodeNum, (reservoirInDegree, (inputReservoirConnectivity, (reservoirSpectralRadius, (inScale, predictAhead))))), index) =>
        val resultDataSetIdSuffix = settings.resultDataSetIndex.map(x => "_" + (x + index)).getOrElse("")
        val resultDataSetNameSuffix = settings.resultDataSetIndex.map(x => " [" + (x + index) + "]").getOrElse("")

        val ioSpec = RCPredictionInputOutputSpec(
          settings.inputSeriesFieldPaths,
          settings.outputSeriesFieldPaths,
          settings.dropLeftLength,
          settings.dropRightLength,
          settings.seriesLength,
          settings.sourceDataSetId,
          settings.resultDataSetId + resultDataSetIdSuffix,
          settings.resultDataSetName + resultDataSetNameSuffix
        )

        // generate a new fixed-value weight RD to be used for all prediction within the same data set
        val weightRd = new RepeatedDistribution(weightRdp.nextList(5000).toArray[jl.Double](Array[jl.Double]()))

        val setting = createReservoirSetting(
          reservoirNodeNum,
          reservoirInDegree,
          settings.reservoirCircularInEdges,
          settings.reservoirFunctionType,
          settings.reservoirFunctionParams,
          inputReservoirConnectivity,
          reservoirSpectralRadius,
          inScale,
          settings.washoutPeriod,
          predictAhead,
          settings.seriesPreprocessingType,
          weightRd
        )

        (setting, ioSpec, settings.batchSize, settings.preserveWeightFieldNames)
    }
  }
}
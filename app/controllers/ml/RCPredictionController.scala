package controllers.ml

import java.{lang => jl}
import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import com.banda.incal.domain.ReservoirLearningSetting
import com.banda.math.business.rand.RandomDistributionProviderFactory
import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.domain.ActivationFunctionType
import controllers.{EnumFormatter, JsonFormatter, SeqFormatter}
import controllers.core.WebContext
import models.ml._
import models.{Field, FieldTypeId, StorageType}
import persistence.RepoTypes.MessageRepo
import persistence.dataset.DataSetAccessorFactory
import play.api.{Configuration, Logger}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.data.format.Formats.doubleFormat
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsNull, JsObject, JsValue}
import play.api.mvc.{Action, Controller, Request}
import services.{DataSpaceService, RCPredictionService}
import util.MessageLogger
import util.SecurityUtil.restrictAdmin

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RCPredictionController @Inject()(
    deadbolt: DeadboltActions,
    messageRepo: MessageRepo,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    configuration: Configuration,
    mPowerWalkingRCPredictionService: RCPredictionService,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  private val mergeMPowerWithDemographics = configuration.getBoolean("mpower.merge_with_demographics").get
  private implicit val stringSeqFormatter = SeqFormatter.apply
  private implicit val intSeqFormatter = SeqFormatter.applyInt
  private implicit val doubleSeqFormatter = SeqFormatter.applyDouble
  private implicit val vectorTransformTypeFormatter = EnumFormatter(VectorTransformType)

  private val rcPredictionSettingsForm = Form(
    mapping(
      "reservoirNodeNums" -> of[Seq[Int]],
      "reservoirInDegrees" -> of[Seq[Int]],
      "inputReservoirConnectivities" -> of[Seq[Double]],
      "reservoirSpectralRadiuses" -> of[Seq[Double]],
      "inScales" -> of[Seq[Double]],
      "seriesPreprocessingType" -> optional(of[VectorTransformType.Value]),
      "washoutPeriods" -> of[Seq[Int]],
      "predictAheads" -> of[Seq[Int]],
      "dropRightLengths" -> of[Seq[Int]],
      "inputSeriesFieldPaths" -> of[Seq[String]],
      "outputSeriesFieldPaths" -> of[Seq[String]],
      "sourceDataSetId" -> nonEmptyText,
      "resultDataSetId" -> nonEmptyText,
      "resultDataSetName" -> nonEmptyText,
      "resultDataSetIndex" -> optional(number(min = 0, max = 2000)),
      "batchSize" -> optional(number(min = 1, max = 200))
    )(RCPredictionSettings.apply)(RCPredictionSettings.unapply))

  private implicit def webContext(implicit request: Request[_]) = WebContext(messagesApi)

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    reservoirInDegree: Int,
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double,
    inScale: Double,
    washoutPeriod: Int,
    _predictAhead: Int,
    _seriesPreprocessingType: Option[VectorTransformType.Value],
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
    setReservoirInDegree(Some(reservoirInDegree))
    setReservoirInDegreeDistribution(None) // Some(RandomDistribution.createPositiveNormalDistribution(classOf[Integer], 50d, 0d))
    setReservoirEdgesNum(None) // Some((0.02 * (250 * 250)).toInt)
    setReservoirPreferentialAttachment(false)
    setReservoirBias(false)
    setInputReservoirConnectivity(inputReservoirConnectivity)
    setReservoirSpectralRadius(reservoirSpectralRadius)
    setReservoirFunctionType(ActivationFunctionType.Tanh)
    setReservoirFunctionParams(None) // Some(Seq(0.5d : jl.Double, 0.25 * math.Pi : jl.Double, 0d : jl.Double))
    setWeightDistribution(weightRd)
    setWashoutPeriod(washoutPeriod)
    predictAhead = _predictAhead
    seriesPreprocessingType = _seriesPreprocessingType
  }

  def showRCPrediction = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      for {
        tree <- dataSpaceService.getTreeForCurrentUser(request)
      } yield
        Ok(views.html.admin.rcPrediction(rcPredictionSettingsForm, tree))
    }
  }

  def runRCPrediction = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
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
            codeDiagnosisJsonMap <- if (mergeMPowerWithDemographics) createHealthCodeDiagnosisJsonMap else Future(Map[String, JsValue]())

            _ <- util.seqFutures(toRCSettings(settings)) { case (setting, ioSpec, batchSize) =>

              mPowerWalkingRCPredictionService.predictAndStoreResults(
                setting, ioSpec, batchSize,
                if (mergeMPowerWithDemographics) Some(transform(codeDiagnosisJsonMap)) else None
              )
            }
          } yield {
            Ok("Hooray")
          }
      )
    }
  }

  private def toRCSettings(
    settings: RCPredictionSettings
  ): Seq[(ExtendedReservoirLearningSetting, RCPredictionInputOutputSpec, Option[Int])] = {

    // get the maximum size of the param seqs
    val maxSize = Seq(
      settings.reservoirNodeNums,
      settings.inputReservoirConnectivities,
      settings.reservoirSpectralRadiuses,
      settings.inScales,
      settings.washoutPeriods,
      settings.predictAheads,
      settings.dropRightLengths
    ).foldLeft(0) { case (minSize, seq) => Math.max(minSize, seq.size) }

    def stream[T](xs: Seq[T]) = Stream.continually(xs).flatten.take(maxSize)

    stream(settings.reservoirNodeNums).zip(
    stream(settings.reservoirInDegrees).zip(
    stream(settings.inputReservoirConnectivities).zip(
    stream(settings.reservoirSpectralRadiuses).zip(
    stream(settings.inScales).zip(
    stream(settings.washoutPeriods).zip(
    stream(settings.predictAheads).zip(
    stream(settings.dropRightLengths)))))))).zipWithIndex.map {

      case ((reservoirNodeNum, (reservoirInDegree, (inputReservoirConnectivity, (reservoirSpectralRadius, (inScale, (washoutPeriod, (predictAhead, dropRightLength))))))), index) =>
        val resultDataSetIdSuffix = settings.resultDataSetIndex.map(x => "_" + (x + index)).getOrElse("")
        val resultDataSetNameSuffix = settings.resultDataSetIndex.map(x => " [" + (x + index) + "]").getOrElse("")

        val ioSpec = RCPredictionInputOutputSpec(
          settings.inputSeriesFieldPaths,
          settings.outputSeriesFieldPaths,
          dropRightLength,
          settings.sourceDataSetId,
          settings.resultDataSetId + resultDataSetIdSuffix,
          settings.resultDataSetName + resultDataSetNameSuffix
        )

        // generate a new fixed-value weight RD to be used for all prediction within the same data set
        val weightRd = new RepeatedDistribution(weightRdp.nextList(5000).toArray[jl.Double](Array[jl.Double]()))

        val setting = createReservoirSetting(
          reservoirNodeNum,
          reservoirInDegree,
          inputReservoirConnectivity,
          reservoirSpectralRadius,
          inScale,
          washoutPeriod,
          predictAhead,
          settings.seriesPreprocessingType,
          weightRd
        )

        (setting, ioSpec, settings.batchSize)
    }
  }

  private val demographicsDataSetId = "mpower_challenge.demographics_training"
  private val demographicsDsa = dsaf(demographicsDataSetId)
  private val professionalDiagnosisField = Field("professional-diagnosis", None, FieldTypeId.Boolean)

  private def createHealthCodeDiagnosisJsonMap =
    demographicsDsa.get.dataSetRepo.find(
      projection = Seq("healthCode", professionalDiagnosisField.name)
    ).map(_.map { json =>
      val healthCode = (json \ "healthCode").as[String]
      val diagnosisJson = (json \ professionalDiagnosisField.name).getOrElse(JsNull)
      (healthCode, diagnosisJson)
      }.toMap
    )

  private def transform(
    codeDiagnosisJsonMap: Map[String, JsValue])(
    jsonsAndFields: (Seq[JsObject], Traversable[Field])
  ) = {
      val newJsons = jsonsAndFields._1.map { json =>
      val healthCode = (json \ "healthCode").as[String]
      val diagnosisJson = codeDiagnosisJsonMap.get(healthCode).getOrElse(JsNull)

      json + ("professional-diagnosis", diagnosisJson)
    }

    val newFields = jsonsAndFields._2 ++ Seq(professionalDiagnosisField)
    (newJsons, newFields)
  }
}
package controllers

import javax.inject.Inject
import java.{lang => jl}

import be.objectify.deadbolt.scala.DeadboltActions
import com.banda.incal.domain.ReservoirLearningSetting
import com.banda.math.business.rand.RandomDistributionProviderFactory
import com.banda.math.domain.rand.{RandomDistribution, RepeatedDistribution}
import com.banda.network.domain.ActivationFunctionType
import controllers.core.WebContext
import models.{DataSetMetaInfo, Field, FieldTypeId}
import models.ml.{RCPredictionSetting, RCPredictionSettings, VectorTransformType}
import play.api.data.Forms._
import persistence.RepoTypes.MessageRepo
import persistence.dataset.DataSetAccessorFactory
import play.api.Configuration
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Request}
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms.{ignored, mapping}
import play.api.data.format.Formats.doubleFormat
import play.api.libs.json.{JsNull, JsObject, JsValue}
import reactivemongo.bson.BSONObjectID
import services.{DataSpaceService, RCPredictionService}
import util.MessageLogger
import util.ReflectionUtil._
import util.SecurityUtil.restrictAdmin

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AdminController @Inject() (
    deadbolt: DeadboltActions,
    messageRepo: MessageRepo,
    dsaf: DataSetAccessorFactory,
    dataSpaceService: DataSpaceService,
    configuration: Configuration,
    mPowerWalkingRCPredictionService: RCPredictionService
  ) extends Controller {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  private val mergeMPowerWithDemographics = configuration.getBoolean("mpower.merge_with_demographics").get
  private implicit val stringSeqFormatter = SeqFormatter.apply
  private implicit val intSeqFormatter = SeqFormatter.applyInt
  private implicit val doubleSeqFormatter = SeqFormatter.applyDouble
  private implicit val vectorTransformTypeFormatter = EnumFormatter(VectorTransformType)

  private val form = Form(
    mapping(
      "reservoirNodeNums" -> of[Seq[Int]],
      "reservoirInDegrees" -> of[Seq[Int]],
      "inputReservoirConnectivities" -> of[Seq[Double]],
      "reservoirSpectralRadiuses" -> of[Seq[Double]],
      "seriesPreprocessingType" -> of[VectorTransformType.Value],
      "washoutPeriods" -> of[Seq[Int]],
      "dropRightLengths" -> of[Seq[Int]],
      "inputSeriesFieldPaths" -> of[Seq[String]],
      "outputSeriesFieldPaths" -> of[Seq[String]],
      "sourceDataSetId" -> nonEmptyText,
      "resultDataSetId" -> nonEmptyText,
      "resultDataSetName" -> nonEmptyText,
      "resultDataSetIndex" -> optional(number(min = 0, max = 2000)),
      "batchSize" -> optional(number(min = 1, max = 200))
    )(RCPredictionSettings.apply)(RCPredictionSettings.unapply))

  @Inject var messagesApi: MessagesApi = _

  // we scan only the jars starting with this prefix to speed up the class search
  private val libPrefix = "ncer-pd"

  private implicit def webContext(implicit request: Request[_]) = WebContext(messagesApi)

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def listRunnables = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val classes = findClasses[Runnable](libPrefix, Some("runnables."), None)

      val runnableNames = classes.map(_.getName).sorted
      Ok(views.html.admin.runnables(runnableNames))
    }
  }

  private val runnablesRedirect = Redirect(routes.AdminController.listRunnables())

  /**
    * Runs the script given its path (i.e. "runnables.denopa.DeNoPaCleanup").
    *
    * @param className Path of runnable to launch.
    * @return Redirects to listRunnables()
    */
  def runScript(className : String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      try {
        val clazz = Class.forName(className)
        val runnable = current.injector.instanceOf(clazz).asInstanceOf[Runnable]
        val start = new java.util.Date()
        runnable.run()
        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."
        messageLogger.info(message)
        runnablesRedirect.flashing("success" -> message)
      } catch {
        case e: ClassNotFoundException => {
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")
        }
        case e: Exception => {
          logger.error(s"Script ${className} failed", e)
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
        }
      }
    }
  }

  private val weightRdp = RandomDistributionProviderFactory(RandomDistribution.createNormalDistribution[jl.Double](classOf[jl.Double], 0d, 1d))

  private def createReservoirSetting(
    reservoirNodeNum: Int,
    reservoirInDegree: Int,
    inputReservoirConnectivity: Double,
    reservoirSpectralRadius: Double,
    weightRd: RandomDistribution[jl.Double]
  ) = new ReservoirLearningSetting {
    setWeightAdaptationIterationNum(2)
    setSingleIterationLength(1d)
    setInitialDelay(0d)
    setInputTimeLength(1d)
    setOutputInterpretationRelativeTime(1d)
    setInScale(1d)
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
  }

  def showRCPrediction = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      for {
        tree <- dataSpaceService.getTreeForCurrentUser(request)
      } yield
        Ok(views.html.admin.mPowerRCPrediction(form, tree))
    }
  }

  def runRCPrediction = restrictAdmin(deadbolt) {
    Action.async { implicit request =>
      form.bindFromRequest.fold(
        { formWithErrors =>
          for {
            tree <- dataSpaceService.getTreeForCurrentUser(request)
          } yield {
            BadRequest(views.html.admin.mPowerRCPrediction(form, tree))
          }
        },
        settings => {
          // important!!! generate a new fixed-value weight RD to be used for all RC calls
          val weightRd = new RepeatedDistribution(weightRdp.nextList(5000).toArray[jl.Double](Array[jl.Double]()))

          for {
            codeDiagnosisJsonMap <- if (mergeMPowerWithDemographics) createHealthCodeDiagnosisJsonMap else Future(Map[String, JsValue]())

            _ <- util.seqFutures(toRCSettings(settings)) { item =>
              mPowerWalkingRCPredictionService.predictAndStoreResults(
                createReservoirSetting(
                  item.reservoirNodeNum,
                  item.reservoirInDegree,
                  item.inputReservoirConnectivity,
                  item.reservoirSpectralRadius,
                  weightRd
                ),
                item.washoutPeriod,
                item.dropRightLength,
                item.inputSeriesFieldPaths,
                item.outputSeriesFieldPaths,
                item.sourceDataSetId,
                item.resultDataSetId,
                item.resultDataSetName,
                item.batchSize,
                if (mergeMPowerWithDemographics) Some(transform(codeDiagnosisJsonMap)) else None
              )
            }
          } yield {
            Ok("Hooray")
          }
        }
      )
    }
  }

  private def toRCSettings(
    settings: RCPredictionSettings
  ): Seq[RCPredictionSetting] = {

    // get the maximum size of the param seqs
    val maxSize = Seq(
      settings.reservoirNodeNums,
      settings.inputReservoirConnectivities,
      settings.reservoirSpectralRadiuses,
      settings.washoutPeriods,
      settings.dropRightLengths
    ).foldLeft(0) { case (minSize, seq) => Math.max(minSize, seq.size) }

    def stream[T](xs: Seq[T]) = Stream.continually(xs).flatten.take(maxSize)

    stream(settings.reservoirNodeNums).zip(
    stream(settings.reservoirInDegrees).zip(
    stream(settings.inputReservoirConnectivities).zip(
    stream(settings.reservoirSpectralRadiuses).zip(
    stream(settings.washoutPeriods).zip(
    stream(settings.dropRightLengths)))))).zipWithIndex.map {

      case ((reservoirNodeNum, (reservoirInDegree, (inputReservoirConnectivity, (reservoirSpectralRadius, (washoutPeriod, dropRightLength))))), index) =>
        val resultDataSetIdSuffix = settings.resultDataSetIndex.map(x => "_" + (x + index)).getOrElse("")
        val resultDataSetNameSuffix = settings.resultDataSetIndex.map(x => " [" + (x + index) + "]").getOrElse("")

        RCPredictionSetting(
          reservoirNodeNum,
          reservoirInDegree,
          inputReservoirConnectivity,
          reservoirSpectralRadius,
          settings.seriesPreprocessingType,
          washoutPeriod,
          dropRightLength,
          settings.inputSeriesFieldPaths,
          settings.outputSeriesFieldPaths,
          settings.sourceDataSetId,
          settings.resultDataSetId + resultDataSetIdSuffix,
          settings.resultDataSetName + resultDataSetNameSuffix,
          settings.batchSize
        )
    }
  }

  private val demographicsDataSetId = "mpower_challenge.demographics_training"
  private val demographicsDsa = dsaf(demographicsDataSetId)
  private val professionalDiagnosisField = Field("professional-diagnosis", None, FieldTypeId.Boolean)

  def createHealthCodeDiagnosisJsonMap =
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
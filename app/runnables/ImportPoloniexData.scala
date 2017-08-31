package runnables

import javax.inject.Inject
import java.{util => ju}

import service.{PoloniexService, PoloniexServiceFactory}
import services.DataSetService
import java.util.Calendar

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsObject, Json}
import model.poloniex.PeriodPriceVolume
import models.ml.RCPredictionSettingAndResults
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import model.poloniex.JsonFormat.periodPriceVolumeFormat
import util.FieldUtil.caseClassToFlatFieldTypes
import java.util

import _root_.util.{seqFutures, retry}
import PriceVolumeData.priceVolumeDataFormat
import models._
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger

import scala.concurrent.Future

case class PriceVolumeData(
  _id: Option[BSONObjectID],
  currencyPair: String,
  period: Int,
  startTime: ju.Date,
  endTime: ju.Date,
  series: Seq[PeriodPriceVolume],
  timeCreated: ju.Date = new ju.Date()
)

object PriceVolumeData {
  implicit val priceVolumeDataFormat = Json.format[PriceVolumeData]
}

class ImportPoloniexData @Inject()(
    poloniexServiceFactory: PoloniexServiceFactory,
    dataSetService: DataSetService,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val poloniexService = poloniexServiceFactory("", "")
  private val priceVolumeDataFields = caseClassToFlatFieldTypes[PriceVolumeData]("-").filter(_._1 != "_id")

  private val dataSpaceName = "BTC"
  private val resultDataSetId = "btc.poloniex"
  private val resultDataSetName = "Poloniex"
  private val resultDataSetSetting = new DataSetSetting(resultDataSetId, StorageType.ElasticSearch, "currencyPair")

  private val logger = Logger

  private val start = Calendar.getInstance
  start.set(2016, 0, 1, 0, 0, 0)
  private val end = Calendar.getInstance
  end.set(2030, 0, 1, 0, 0, 0)

  private val currencyPairSymbols = Seq("BTC_ETH", "BTC_ZEC", "BTC_LTC", "BTC_XRP", "BTC_DASH", "BTC_STEEM")
  private val periods = Seq(300, 900, 1800, 7200, 14400, 86400)

  override def runAsFuture =
    for {
      priceVolumeData <-
        seqFutures(
          for {
            a <- currencyPairSymbols; b <- periods
          } yield
            (a, b)
        ) { case (currencyPair, period) =>
          retry(s"Poloniex data $currencyPair download failed:", logger, 3)(
            getPriceVolumeData(currencyPair, period)
          )
        }

      _ <- {
        logger.info(s"Saving ${priceVolumeData.length} price-volume series.")
        saveData(priceVolumeData)
      }
    } yield
      ()

  private def getPriceVolumeData(
    currencyPair: String,
    period: Int
  ): Future[PriceVolumeData] =
    for {
      priceVolumes <- poloniexService.publicChartData(
        currencyPair,
        period,
        Some(start.getTime),
        Some(end.getTime)
      )
    } yield {
      val startDate = new ju.Date(priceVolumes.head.date * 1000)
      val endDate = new ju.Date(priceVolumes.last.date * 1000)

      logger.info(s"Got $currencyPair for the period $period.")
      PriceVolumeData(None, currencyPair, period, startDate, endDate, priceVolumes)
    }

  private def saveData(
    items: Traversable[PriceVolumeData]
  ): Future[Unit] =
    for {
    // register the results data set (if not registered already)
      newDsa <- dsaf.register(dataSpaceName, resultDataSetId, resultDataSetName, Some(resultDataSetSetting), Some(mainDataView))

      // update the dictionary
      _ <- dataSetService.updateDictionary(resultDataSetId, priceVolumeDataFields, false, true)

//      // delete old
//      _ <- newDsa.dataSetRepo.deleteAll

      // save the results
      _ <- dataSetService.saveOrUpdateRecords(newDsa.dataSetRepo, items.map(Json.toJson(_).as[JsObject]).toSeq, None, false, None, Some(1))
    } yield
      ()

  private val mainDataView: DataView = {
    val distributionWidgets = Seq(
      DistributionWidgetSpec("currencyPair", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Bar))),
      DistributionWidgetSpec("period", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Column))),
      DistributionWidgetSpec("startTime", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Line))),
      DistributionWidgetSpec("timeCreated", None, displayOptions = MultiChartDisplayOptions(chartType = Some(ChartType.Line)))
    )

    DataView(
      None, "Main", Nil,
      Seq("currencyPair", "period", "timeCreated", "data"),
      distributionWidgets,
      3,
      true
    )
  }
}

object ImportPoloniexData extends GuiceBuilderRunnable[ImportPoloniexData] with App { run }
package controllers.mpower

import javax.inject.Inject

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.WebJarAssets
import controllers.core.WebContext
import dataaccess.JsonRepoExtra._
import dataaccess.Criterion._
import models._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, AnyContent, Controller, Request}
import services.WidgetGenerationService
import views.html.mpowerchallenge.clustering

class MPowerChallengeClusteringController @Inject()(
    dsaf: DataSetAccessorFactory,
    widgetGenerationService: WidgetGenerationService,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller {

  private val x1 = "x1"
  private val x2 = "x2"
  private val clazz = "clazz"
  private val category = "Category"
  private val team = "Team"
  private val auroc = "AUROC_Unbiased_Subset"
  private val aupr = "AUPR"

  private val logger = Logger

  private val widgetSpecs = Seq(
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      groupFieldName = Some(clazz),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(750), title = Some("X1 vs X2 by Clusterization Class"))
    ),
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      groupFieldName = Some(category),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(750), title = Some("X1 vs X2 by Feature Category"))
    ),
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      groupFieldName = Some(team),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(750), title = Some("X1 vs X2 by Team"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = auroc,
      xBinCount = 50,
      yBinCount = 50,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = aupr,
      xBinCount = 50,
      yBinCount = 50,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by AUPR Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = auroc,
      xBinCount = 20,
      yBinCount = 20,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = aupr,
      xBinCount = 20,
      yBinCount = 20,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by AUPR Mean"))
    ),
    DistributionWidgetSpec(
      fieldName = auroc,
      groupFieldName = Some(clazz),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(8), gridOffset = Some(2), height = Some(400), title = Some("AUROC by Clusterization Class")
      )
    ),
    DistributionWidgetSpec(
      fieldName = aupr,
      groupFieldName = Some(clazz),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(8), gridOffset = Some(2), height = Some(400), title = Some("AUPR by Clusterization Class")
      )
    ),
    DistributionWidgetSpec(
      fieldName = x1,
      groupFieldName = Some(category),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("X1 by Feature Category")
      )
    ),
    DistributionWidgetSpec(
      fieldName = x2,
      groupFieldName = Some(category),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("X2 by Feature Category")
      )
    ),
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = auroc,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = x2,
      yFieldName = auroc,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = aupr,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = x2,
      yFieldName = aupr,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    )
  )

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  def index = Action { implicit request =>
    Ok(views.html.mpowerchallenge.clusteringHome())
  }

  // MDS

  def tremorMDS(
    topRank: Option[Int],
    k: Int,
    method: Int
  ) = clusterizationAux(
    "Tremor Features", tremorMDSDsa(k, method), "Rank", topRank, k, method, None, false, false
  )

  def dyskinesiaMDS(
    topRank: Option[Int],
    k: Int,
    method: Int
  ) = clusterizationAux(
    "Dyskinesia Features", dyskinesiaMDSDsa(k, method), "Rank", topRank, k, method, None, false, false
  )

  def bradykinesiaMDS(
    topRank: Option[Int],
    k: Int,
    method: Int
  ) = clusterizationAux(
    "Bradykinesia Features", bradykinesiaMDSDsa(k, method), "Rank", topRank, k, method, None, false, false
  )

  def mPowerMDS(
    topRank: Option[Int],
    k: Int,
    method: Int
  ) = clusterizationAux(
    "mPower Features", mPowerMDSDsa(k, method), "Rank_Unbiased_Subset", topRank, k, method, None, false, false
  )

  private def tremorMDSDsa(
    k: Int,
    method: Int
  ) = dsa(s"harvard_ldopa.tremor-scaled-mds_eigen_unscaled-${methodToString(method)}kmeans_${k}_iter_50")

  private def dyskinesiaMDSDsa(
    k: Int,
    method: Int
  ) = dsa(s"harvard_ldopa.dyskinesia-scaled-mds_eigen_unscaled-${methodToString(method)}kmeans_${k}_iter_50")

  private def bradykinesiaMDSDsa(
    k: Int,
    method: Int
  ) = dsa(s"harvard_ldopa.bradykinesia-scaled-mds_eigen_unscaled-${methodToString(method)}kmeans_${k}_iter_50")

  private def mPowerMDSDsa(
    k: Int,
    method: Int
  ) = dsa(s"mpower_challenge.mpower-scaled-mds_eigen_unscaled-${methodToString(method)}kmeans_${k}_iter_50")

  // t-SNE

  def tremorTSNE(
    topRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = clusterizationAux(
    "Tremor Features", tremorTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, k, method, pcaDims, scaled, true
  )

  def dyskinesiaTSNE(
    topRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = clusterizationAux(
    "Dyskinesia Features", dyskinesiaTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, k, method, pcaDims, scaled, true
  )

  def bradykinesiaTSNE(
    topRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = clusterizationAux(
    "Bradykinesia Features", bradykinesiaTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, k, method, pcaDims, scaled, true
  )

  // no pca for mPower t-SNE (TODO)
  def mPowerTSNE(
    topRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = clusterizationAux(
    "mPower Features", mPowerTSNEDsa(k, method, None, scaled), "Rank_Unbiased_Subset", topRank, k, method, None, scaled, true
  )

  private def tremorTSNEDsa(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = dsa(s"harvard_ldopa.tremor${scaledToString(scaled)}-cols-2d_iter-4000_per-20_0_theta-0_25${pcaDimsToString(pcaDims)}-${methodToString(method)}kmeans_${k}_iter_50")

  private def dyskinesiaTSNEDsa(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = dsa(s"harvard_ldopa.dyskinesia${scaledToString(scaled)}-cols-2d_iter-4000_per-20_0_theta-0_25${pcaDimsToString(pcaDims)}-${methodToString(method)}kmeans_${k}_iter_50")

  private def bradykinesiaTSNEDsa(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = dsa(s"harvard_ldopa.bradykinesia${scaledToString(scaled)}-cols-2d_iter-4000_per-20_0_theta-0_25${pcaDimsToString(pcaDims)}-${methodToString(method)}kmeans_${k}_iter_50")

  private def mPowerTSNEDsa(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean
  ) = dsa(s"mpower_challenge.mpower${scaledToString(scaled)}-cols-2d_iter-4000_per-20_0_theta-0_25${pcaDimsToString(pcaDims)}-${methodToString(method)}kmeans_${k}_iter_50")

  private def dsa(dataSetId: String) = dsaf(dataSetId).getOrElse(
    throw new AdaException(s"Data set $dataSetId does not exist.")
  )

  private def methodToString(method: Int) =
    method match {
      case 1 => ""
      case 2 => "bis"
      case _ => throw new AdaException(s"Clusterization method $method unrecognized.")
    }

  private def scaledToString(scaled: Boolean) =
    scaled match {
      case true => "-scaled"
      case false => ""
    }

  private def pcaDimsToString(pcaDims: Option[Int]) =
    pcaDims match {
      case Some(pcaDims) => s"_pca-$pcaDims"
      case None => ""
    }

  private def clusterizationAux(
    title: String,
    dsa: DataSetAccessor,
    rankFieldName: String,
    topRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    isTSNE: Boolean
  ) = Action.async { implicit request =>
    val criteria = topRank.map(rank => Seq(rankFieldName #<= rank)).getOrElse(Nil)

    for {
      // get all the fields
      fields <- dsa.fieldRepo.find()

      // get the count
      count <- dsa.dataSetRepo.count(criteria)

      // get the max rank
      rankJson <- dsa.dataSetRepo.max(rankFieldName, Nil)

      // create widgets
      widgets <- {
        val fieldNameSet = fields.map(_.name).toSet

        val filteredWidgetSpecs = widgetSpecs.filter(_.fieldNames.forall(fieldNameSet.contains))

        widgetGenerationService(
          widgetSpecs = filteredWidgetSpecs,
          repo = dsa.dataSetRepo,
          criteria = criteria,
          fields = fields,
          genMethod = WidgetGenerationMethod.FullData
        )
      }
    } yield {
      def round4(value: Double) = Math.round(value * 10000).toDouble / 10000

      val newWidgets = widgets.flatten.map { widget =>
        widget match {
          case x: ScatterWidget[Double, Double] =>
            val newData =
              x.data.map { case (label, series) => (label, series.map { case (x, y) => (round4(x), round4(y)) })}
            x.copy(data = newData)

          case _ => widget
        }
      }
      Ok(clustering(title, newWidgets, count, rankJson.get.as[Int], topRank, k, method, pcaDims, scaled, isTSNE))
    }
  }
}
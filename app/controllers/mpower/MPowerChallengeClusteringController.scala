package controllers.mpower

import javax.inject.Inject
import models.ScatterWidget
import org.ada.server.AdaException
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.models._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.incal.core.dataaccess.Criterion._
import org.incal.play.controllers._
import org.incal.play.security.AuthAction
import play.api.Logger
import services.WidgetGenerationService
import views.html.mpowerchallenge.clustering
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class MPowerChallengeClusteringController @Inject() (
    dsaf: DataSetAccessorFactory,
    widgetGenerationService: WidgetGenerationService
  ) extends BaseController {

  private val x1 = "x1"
  private val x2 = "x2"
  private val clazz = "clazz"
  private val category = "Category"
  private val team = "Team"
  private val auroc = "AUROC_Unbiased_Subset"
  private val individualAuroc = "Individual_AUROC"
  private val aupr = "AUPR"

  private val logger = Logger

  private val widgetSpecs = Seq(
    ScatterWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      groupFieldName = Some(clazz),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(750), title = Some("X1 vs X2 by Clustering Class"))
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
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Submission AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = individualAuroc,
      xBinCount = 50,
      yBinCount = 50,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Individual AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = aupr,
      xBinCount = 50,
      yBinCount = 50,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Submission AUPR Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = auroc,
      xBinCount = 20,
      yBinCount = 20,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Submission AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = individualAuroc,
      xBinCount = 20,
      yBinCount = 20,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Individual AUROC Mean"))
    ),
    HeatmapAggWidgetSpec(
      xFieldName = x1,
      yFieldName = x2,
      valueFieldName = aupr,
      xBinCount = 20,
      yBinCount = 20,
      aggType = AggType.Mean,
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(850), title = Some("X1 vs X2 by Submission AUPR Mean"))
    ),
    DistributionWidgetSpec(
      fieldName = auroc,
      groupFieldName = Some(clazz),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(8), gridOffset = Some(2), height = Some(400), title = Some("Submission AUROC by Clustering Class")
      )
    ),
    DistributionWidgetSpec(
      fieldName = individualAuroc,
      groupFieldName = Some(clazz),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(8), gridOffset = Some(2), height = Some(400), title = Some("Individual AUROC by Clustering Class")
      )
    ),
    DistributionWidgetSpec(
      fieldName = aupr,
      groupFieldName = Some(clazz),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(8), gridOffset = Some(2), height = Some(400), title = Some("Submission AUPR by Clustering Class")
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
      yFieldName = individualAuroc,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = x2,
      yFieldName = individualAuroc,
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

  def index = AuthAction { implicit request =>
    Future(Ok(views.html.mpowerchallenge.clusteringHome()))
  }

  // MDS

  def tremorMDS(
    k: Int,
    method: Int,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Tremor Features", tremorMDSDsa(k, method), "Rank", topRank, leaveTopRank, k, method, None, false, false
  )

  def dyskinesiaMDS(
    k: Int,
    method: Int,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Dyskinesia Features", dyskinesiaMDSDsa(k, method), "Rank", topRank, leaveTopRank, k, method, None, false, false
  )

  def bradykinesiaMDS(
    k: Int,
    method: Int,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Bradykinesia Features", bradykinesiaMDSDsa(k, method), "Rank", topRank, leaveTopRank, k, method, None, false, false
  )

  def mPowerMDS(
    k: Int,
    method: Int,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "mPower Features", mPowerMDSDsa(k, method), "Rank_Unbiased_Subset", topRank, leaveTopRank, k, method, None, false, false
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
  ) = dsa(s"mpower_challenge.mpower-scaled-mds_eigen_unscaled-${methodToString(method)}kmeans_${k}_iter_50_ext")

  // t-SNE

  def tremorTSNE(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Tremor Features", tremorTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, leaveTopRank, k, method, pcaDims, scaled, true
  )

  def dyskinesiaTSNE(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Dyskinesia Features", dyskinesiaTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, leaveTopRank, k, method, pcaDims, scaled, true
  )

  def bradykinesiaTSNE(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "Bradykinesia Features", bradykinesiaTSNEDsa(k, method, pcaDims, scaled), "Rank", topRank, leaveTopRank, k, method, pcaDims, scaled, true
  )

  // no pca for mPower t-SNE (TODO)
  def mPowerTSNE(
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    topRank: Option[Int],
    leaveTopRank: Option[Int]
  ) = clusterizationAux(
    "mPower Features", mPowerTSNEDsa(k, method, None, scaled), "Rank_Unbiased_Subset", topRank, leaveTopRank, k, method, None, scaled, true
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
  ) = dsa(s"mpower_challenge.mpower${scaledToString(scaled)}-cols-2d_iter-4000_per-20_0_theta-0_25${pcaDimsToString(pcaDims)}-${methodToString(method)}kmeans_${k}_iter_50_ext")

  private def dsa(dataSetId: String) = dsaf(dataSetId).getOrElse(
    throw new AdaException(s"Data set $dataSetId does not exist.")
  )

  private def methodToString(method: Int) =
    method match {
      case 1 => ""
      case 2 => "bis"
      case _ => throw new AdaException(s"Clustering method $method unrecognized.")
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
    leaveTopRank: Option[Int],
    k: Int,
    method: Int,
    pcaDims: Option[Int],
    scaled: Boolean,
    isTSNE: Boolean
  ) = AuthAction { implicit request =>
    val criteria = Seq(
      topRank.map(rank => rankFieldName #<= rank),
      leaveTopRank.map(rank => rankFieldName #> rank)
    ).flatten

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
      Ok(clustering(title, newWidgets, count, rankJson.get.as[Int], topRank, leaveTopRank, k, method, pcaDims, scaled, isTSNE))
    }
  }
}
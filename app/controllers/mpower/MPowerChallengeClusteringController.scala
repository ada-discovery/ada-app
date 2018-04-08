package controllers.mpower

import javax.inject.Inject

import be.objectify.deadbolt.scala.AuthenticatedRequest
import controllers.WebJarAssets
import controllers.core.WebContext
import dataaccess.{AscSort, DescSort}
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

  private lazy val tremorMDSDsa = dsaf("harvard_ldopa.tremor_feature_mds").get
  private lazy val dyskinesiaMDSDsa = dsaf("harvard_ldopa.dyskinesia_feature_mds").get
  private lazy val bradykinesiaMDSDsa = dsaf("harvard_ldopa.bradykinesia_feature_mds").get
  private lazy val mPowerMDSDsa = dsaf("mpower_challenge.feature_mds").get

  private val mdsX1 = "mds_x1"
  private val mdsX2 = "mds_x2"
  private val category = "Category"
  private val team = "Team"
  private val auroc = "AUROC_Unbiased_Subset"
  private val aupr = "AUPR"

  private val logger = Logger

  private val widgetSpecs = Seq(
    ScatterWidgetSpec(
      xFieldName = mdsX1,
      yFieldName = mdsX2,
      groupFieldName = Some(category),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(700), title = Some("MDS X1 vs MDS X2 by Feature Category"))
    ),
    ScatterWidgetSpec(
      xFieldName = mdsX1,
      yFieldName = mdsX2,
      groupFieldName = Some(team),
      displayOptions = BasicDisplayOptions(gridWidth = Some(12), height = Some(700), title = Some("MDS X1 vs MDS X2 by Team"))
    ),
    DistributionWidgetSpec(
      fieldName = mdsX1,
      groupFieldName = Some(category),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("MDS X1 by Feature Category")
      )
    ),
    DistributionWidgetSpec(
      fieldName = mdsX2,
      groupFieldName = Some(category),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("MDS X2 by Feature Category")
      )
    ),
    DistributionWidgetSpec(
      fieldName = mdsX1,
      groupFieldName = Some(team),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("MDS X1 by Team")
      )
    ),
    DistributionWidgetSpec(
      fieldName = mdsX2,
      groupFieldName = Some(team),
      relativeValues = true,
      displayOptions = MultiChartDisplayOptions(
        chartType = Some(ChartType.Spline), gridWidth = Some(6), title = Some("MDS X2 by Team")
      )
    ),
    ScatterWidgetSpec(
      xFieldName = mdsX1,
      yFieldName = auroc,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = mdsX2,
      yFieldName = auroc,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = mdsX1,
      yFieldName = aupr,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    ),
    ScatterWidgetSpec(
      xFieldName = mdsX2,
      yFieldName = aupr,
      groupFieldName = None,
      displayOptions = BasicDisplayOptions(gridWidth = Some(6), height = None)
    )
  )

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  def tremorMDS(topRank: Option[Int]) = mdsAux(
    "Tremor Features", tremorMDSDsa, "Rank", topRank
  )

  def dyskinesiaMDS(topRank: Option[Int]) = mdsAux(
    "Dyskinesia Features", dyskinesiaMDSDsa, "Rank", topRank
  )

  def bradykinesiaMDS(topRank: Option[Int]) = mdsAux(
    "Bradykinesia Features", bradykinesiaMDSDsa, "Rank", topRank
  )

  def mPowerMDS(topRank: Option[Int]) = mdsAux(
    "mPower Features", mPowerMDSDsa, "Rank_Unbiased_Subset", topRank
  )

  private def mdsAux(
    title: String,
    dsa: DataSetAccessor,
    rankFieldName: String,
    topRank: Option[Int]
  ) = Action.async { implicit request =>
    val criteria = topRank.map(rank => Seq(rankFieldName #<= rank)).getOrElse(Nil)

    for {
      // get all the fields
      fields <- dsa.fieldRepo.find()

      // get the count
      count <- dsa.dataSetRepo.count(criteria)

      // get the max rank
      rankJson <- dsa.dataSetRepo.find(
        projection = Seq(rankFieldName), sort = Seq(DescSort(rankFieldName)), limit = Some(1)
      ).map(_.head \ rankFieldName)

      // create widgets
      widgetsAndFieldNames <- {
        val fieldNameSet = fields.map(_.name).toSet

        val filteredWidgetSpecs = widgetSpecs.filter(_.fieldNames.forall(fieldNameSet.contains))

        widgetGenerationService.apply(
          widgetSpecs = filteredWidgetSpecs,
          repo = dsa.dataSetRepo,
          criteria = criteria,
          fields = fields
        )
      }
    } yield {
      val widgets = widgetsAndFieldNames.flatMap(_.map(_._1))

      def round4(value: Double) = Math.round(value * 10000).toDouble / 10000

      val newWidgets = widgets.map { widget =>
        widget match {
          case x: ScatterWidget[Double, Double] =>
            val newData =
              x.data.map { case (label, series) => (label, series.map { case (x, y) => (round4(x), round4(y)) })}
            x.copy(data = newData)

          case _ => widget
        }
      }
      Ok(clustering(title, newWidgets, count, rankJson.as[Int], topRank))
    }
  }
}
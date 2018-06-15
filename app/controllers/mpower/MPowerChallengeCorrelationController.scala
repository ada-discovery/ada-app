package controllers.mpower

import javax.inject.Inject

import _root_.util.{GroupMapList, seqFutures}
import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.WebJarAssets
import controllers.core.WebContext
import dataaccess.Criterion._
import models.AdaException
import models.DataSetFormattersAndIds.FieldIdentity
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, AnyContent, Controller, Request}

import scala.concurrent.Future

class MPowerChallengeCorrelationController @Inject()(
    dsaf: DataSetAccessorFactory,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller {

  private val tremorCorrDataSetPrefix = "harvard_ldopa.tremor_correlation_abs"
  private lazy val tremorScoreBoardDsa = dsaf("harvard_ldopa.score_board_tremor_ext").get
  private lazy val tremorFeatureInfoDsa = dsaf("harvard_ldopa.tremor_feature_info").get

  private val dyskinesiaCorrDateSetPrefix = "harvard_ldopa.dyskinesia_correlation_abs"
  private lazy val dyskinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_dyskinesia_ext").get
  private lazy val dyskinesiaFeatureInfoDsa = dsaf("harvard_ldopa.dyskinesia_feature_info").get

  private val bradykinesiaCorrDataSetPrefix = "harvard_ldopa.bradykinesia_correlation_abs"
  private lazy val bradykinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_bradykinesia_ext").get
  private lazy val bradykinesiaFeatureInfoDsa = dsaf("harvard_ldopa.bradykinesia_feature_info").get

  private val mPowerCorrDataSetPrefix = "mpower_challenge.correlation_abs"
  private lazy val mPowerScoreBoardDsa = dsaf("mpower_challenge.score_board_ext").get
  private lazy val mPowerFeatureInfoDsa = dsaf("mpower_challenge.feature_info").get

  private val defaultAbsCorrMeanCutoff = 0.5

  private val logger = Logger

  private implicit val ldopaScoreSubmissionFormat = Json.format[LDOPAScoreSubmissionInfo]
  private implicit val mPowerScoreSubmissionFormat = Json.format[mPowerScoreSubmissionInfo]

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  def index = Action { implicit request =>
    Ok(views.html.mpowerchallenge.correlationHome())
  }

  def tremorTeamNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(tremorCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showTeamCorrelationNetwork(
      "LDOPA Tremor Subchallenge Team Correlation",
      correlationDsa,
      tremorScoreBoardDsa,
      tremorFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def dyskinesiaTeamNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(dyskinesiaCorrDateSetPrefix, aggOut, aggIn, withDemographics)

    showTeamCorrelationNetwork(
      "LDOPA Dyskinesia Subchallenge Team Correlation",
      correlationDsa,
      dyskinesiaScoreBoardDsa,
      dyskinesiaFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def bradykinesiaTeamNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(bradykinesiaCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showTeamCorrelationNetwork(
      "LDOPA Bradykinesia Subchallenge Team Correlation",
      correlationDsa,
      bradykinesiaScoreBoardDsa,
      bradykinesiaFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def mPowerTeamNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(mPowerCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showTeamCorrelationNetwork(
      "mPower Subchallenge Team Correlation",
      correlationDsa,
      mPowerScoreBoardDsa,
      mPowerFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def tremorSubmissionNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(tremorCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showSubmissionCorrelationNetwork(
      "LDOPA Tremor Subchallenge Submission Correlation",
      correlationDsa,
      tremorScoreBoardDsa,
      tremorFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def dyskinesiaSubmissionNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(dyskinesiaCorrDateSetPrefix, aggOut, aggIn, withDemographics)

    showSubmissionCorrelationNetwork(
      "LDOPA Dyskinesia Subchallenge Submission Correlation",
      correlationDsa,
      dyskinesiaScoreBoardDsa,
      dyskinesiaFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def bradykinesiaSubmissionNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(bradykinesiaCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showSubmissionCorrelationNetwork(
      "LDOPA Bradykinesia Subchallenge Submission Correlation",
      correlationDsa,
      bradykinesiaScoreBoardDsa,
      bradykinesiaFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  def mPowerSubmissionNetwork(
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean,
    corrThreshold: Option[Double]
  ) = Action.async { implicit request =>
    val correlationDsa = getCorrelationDsa(mPowerCorrDataSetPrefix, aggOut, aggIn, withDemographics)

    showSubmissionCorrelationNetwork(
      "mPower Subchallenge Submission Correlation",
      correlationDsa,
      mPowerScoreBoardDsa,
      mPowerFeatureInfoDsa,
      corrThreshold,
      aggOut,
      aggIn,
      withDemographics
    )
  }

  private def getCorrelationDsa(
    prefix: String,
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean
  ): DataSetAccessor = {
    val dataSetId = s"${prefix}_${aggOut.toString}_${aggIn.toString}"
    if (withDemographics)
      dsaf(dataSetId).get
    else
      dsaf(dataSetId + "_wo_dem").get
  }

  private def showTeamCorrelationNetwork(
    domainName: String,
    corrDsa: DataSetAccessor,
    scoreBoardDsa: DataSetAccessor,
    featureInfoDsa: DataSetAccessor,
    corrThreshold: Option[Double],
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean)(
    implicit request: Request[AnyContent]
  ) = {
    val threshold = corrThreshold.getOrElse(defaultAbsCorrMeanCutoff)
    for {
      // get all the scored submission infos
      submissionInfos: Traversable[SubmissionInfo] <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map ( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // cross submission mean abs correlations
      correlationAggregates <- calcCrossTeamMeanAbsCorrelations(scoreBoardDsa, corrDsa)

      // demographic features grouped by submission id
      submissionIdDemographicFeaturesCountMap <- countDemographicFeaturesForSubmissions(submissionInfos, featureInfoDsa)
    } yield {
      val teamSubmissionInfos = submissionInfos.groupBy(_.Team).toSeq

      val teamMeanRanks = teamSubmissionInfos.map { case (team, submissions) =>
        val ranks = submissions.flatMap(_.RankFinal)
        val meanRank = ranks.sum.toDouble / submissions.size
        (team, meanRank)
      }.sortBy((_._2))

      val teamIndexMap = teamMeanRanks.map(_._1).zipWithIndex.toMap

      val maxRank = submissionInfos.flatMap(_.RankFinal).max
      val nodes = teamSubmissionInfos.flatMap { case (team, submissions) =>

        val ranks = submissions.flatMap(_.Rank)
        val unbiasedRanks = submissions.flatMap(_.Rank_Unbiased_Subset)
        val fullRanks = submissions.flatMap(_.Rank_Full)
        val auprs = submissions.flatMap(_.AUPR)
        val unbiasedAuprs = submissions.flatMap(_.AUPR_Unbiased_Subset)
        val fullAurocs = submissions.flatMap(_.AUROC_Full)
        val unbiasedAurocs = submissions.flatMap(_.AUROC_Unbiased_Subset)
        val featureNums = submissions.flatMap(_.featureNum)
        val demographicFeatureNums = submissions.flatMap(submissionInfo =>
          submissionInfo.submissionIdInt.map( submissiondId =>
            submissionIdDemographicFeaturesCountMap.get(submissiondId).getOrElse(0)
          )
        )

        val finalRanks = submissions.flatMap(_.RankFinal)
        val meanRank = if (finalRanks.nonEmpty)
          finalRanks.sum / finalRanks.size
        else
          0
        val index = teamIndexMap.get(team).get
        if (meanRank > 0) {
          val data = Json.obj(
            "Ranks" -> ranks,
            "Unbiased Ranks" -> unbiasedRanks,
            "Full Ranks" -> fullRanks,
            "Unbiased AUROCs" -> unbiasedAurocs,
            "Full AUROCs" -> fullAurocs,
            "Unbiased AUPRs" -> unbiasedAuprs,
            "AUPRs" -> auprs,
            "# Features" -> featureNums,
            "# Demographic Feat." -> demographicFeatureNums
          )

          Some(VisNode(index, 5 + (maxRank - meanRank), team, Some(data)))
        } else
          None
      }.sortBy(_.id)

      // edges
      val edges = correlationAggregates.filter{ case ((team1, team2), _) => team1 != team2 }.flatMap {
        case ((team1, team2), corrAggregate) =>
          val index1 = teamIndexMap.get(team1).get
          val index2 = teamIndexMap.get(team2).get
          val definedAggs = corrAggregate.flatten

          if (definedAggs.nonEmpty) {
            val corrAggsMean = definedAggs.sum / definedAggs.size
            if (corrAggsMean > threshold) {
              Some(VisEdge(index1, index2, 2 + (corrAggsMean) * 10, f"$corrAggsMean%1.2f"))
            } else
              None
          } else
            None
      }
      Ok(views.html.mpowerchallenge.correlationNetwork(domainName, threshold, aggOut, aggIn, withDemographics, nodes, edges))
    }
  }

  private def showSubmissionCorrelationNetwork(
    domainName: String,
    corrDsa: DataSetAccessor,
    scoreBoardDsa: DataSetAccessor,
    featureInfoDsa: DataSetAccessor,
    corrThreshold: Option[Double],
    aggOut: AggFunction.Value,
    aggIn: AggFunction.Value,
    withDemographics: Boolean)(
    implicit request: Request[AnyContent]
  ) = {
    val threshold = corrThreshold.getOrElse(defaultAbsCorrMeanCutoff)
    for {
      // get all the scored submission infos
      submissionInfos: Traversable[SubmissionInfo] <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map ( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // cross submission mean abs correlations
      correlationAggregates <- calcCrossSubmissionMeanAbsCorrelations(corrDsa)

      // demographic features grouped by submission id
      submissionIdDemographicFeaturesCountMap <- countDemographicFeaturesForSubmissions(submissionInfos, featureInfoDsa)
    } yield {
      val sortedSubmissions =  submissionInfos.collect{ case x if x.RankFinal.isDefined && x.submissionIdInt.isDefined => x}.toSeq.sortBy(_.RankFinal.get)
      val submissionIndexMap = sortedSubmissions.map(_.submissionIdInt.get).zipWithIndex.toMap

      val maxRank = submissionInfos.flatMap(_.RankFinal).max
      val nodes = sortedSubmissions.zipWithIndex.map { case (submission, index) =>
        val demographicFeaturesNum = submissionIdDemographicFeaturesCountMap.get(submission.submissionIdInt.get).getOrElse(0)

        val data = Json.obj(
          "Submission Id" -> submission.submissionIdInt.get,
          "Rank" -> submission.Rank,
          "Unbiased Rank" -> submission.Rank_Unbiased_Subset,
          "Full Rank" -> submission.Rank_Full,
          "AUPR" -> submission.AUPR,
          "Unbiased AUPR" -> submission.AUPR_Unbiased_Subset,
          "Full AUROC" -> submission.AUROC_Full,
          "Unbiased AUROC" -> submission.AUROC_Unbiased_Subset,
          "# Features" -> submission.featureNum,
          "# Demographic Feat." -> demographicFeaturesNum
        )

        VisNode(index, 5 + (maxRank - submission.RankFinal.get), submission.Team, Some(data))
      }

      val edges = correlationAggregates.filter{ case (sub1, sub2, _) => sub1 != sub2 }.flatMap {
        case (submissionId1, submissionId2, corrAggregate) =>
          val index1 = submissionIndexMap.get(submissionId1).get
          val index2 = submissionIndexMap.get(submissionId2).get

          corrAggregate.flatMap { corrAggregate =>
            if (corrAggregate > threshold) {
              Some(VisEdge(index1, index2, 2 + (corrAggregate) * 10, f"$corrAggregate%1.2f"))
            } else
              None
          }
      }
      Ok(views.html.mpowerchallenge.correlationNetwork(domainName, threshold, aggOut, aggIn, withDemographics, nodes, edges))
    }
  }

  private def calcCrossTeamMeanAbsCorrelations(
    scoreBoardDsa: DataSetAccessor,
    correlationDsa: DataSetAccessor,
    corrDsa: Future[Map[Int, Set[String]]] = Future(Map())
  ): Future[Traversable[((String, String), Traversable[Option[Double]])]] =
    for {
      // create a submission id -> team map for a quick lookup
      submissionIdTeamMap <- scoreBoardDsa.dataSetRepo.find().map(
        _.flatMap { json =>
          val submission = json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
          submission.submissionIdInt.map { submissionId =>
            (submissionId, submission.Team)
          }
        }
      ).map(_.toMap)

      // get the submission aggregates
      submissionAggregates <- calcCrossSubmissionMeanAbsCorrelations(correlationDsa)
    } yield {
      // group aggregates by team pairs
      submissionAggregates.map { case (submissionId1, submissionId2, aggValue) =>
        val team1 = submissionIdTeamMap.get(submissionId1).get
        val team2 = submissionIdTeamMap.get(submissionId2).get
        ((team1, team2), aggValue)
      }.toGroupMap
    }

  private def calcCrossSubmissionMeanAbsCorrelations(
    correlationDsa: DataSetAccessor
  ): Future[Traversable[(Int, Int, Option[Double])]] =
    for {
      submissionFields <- correlationDsa.fieldRepo.find(Seq((FieldIdentity.name) #!= "submissionId"))

      aggregates <- correlationDsa.dataSetRepo.find().map { jsons =>
        jsons.flatMap { json =>
          val submissionId1 = (json \ "submissionId").as[Int]
          submissionFields.map { submissionField =>
            val submissionId2 = submissionField.name.toInt
            val aggValue = (json \ submissionField.name).asOpt[Double]
            (submissionId1, submissionId2, aggValue)
          }
        }
      }
    } yield
      aggregates

  private def countDemographicFeaturesForSubmissions(
    submissionInfos: Traversable[SubmissionInfo],
    featureInfoDsa: DataSetAccessor
  ): Future[Map[Int, Int]] =
    for {
      categoryField <- featureInfoDsa.fieldRepo.get("Category")

      counts <- {
        val field = categoryField.getOrElse(throw new AdaException("Field Category not found"))
        field.numValues.get.find(_._2.equals("demographic")).map(_._1.toInt) match {
          case Some(demographicValue) =>

            Future.sequence(
              submissionInfos.map { submissionInfo =>
                submissionInfo.submissionIdInt.map(id =>
                  featureInfoDsa.dataSetRepo.count(
                    criteria = Seq("Category" #== demographicValue, "SubmissionId" #== id)
                  ).map(count => Some((id, count)))
                ).getOrElse(
                  Future(None)
                )
              }
            ).map(_.flatten.toMap)

          case None => Future(Map[Int, Int]())
        }
      }
    } yield
      counts
}

case class VisNode(id: Int, size: Int, label: String, data: Option[JsValue])

case class VisEdge(from: Int, to: Int, value: Double, label: String)

trait SubmissionInfo {
  def Team: String
  def Rank: Option[Int]
  def submissionIdInt: Option[Int]
  def featureNum: Option[Int]

  def AUPR: Option[Double]
  def AUPR_Unbiased_Subset: Option[Double]
  def AUROC_Full: Option[Double]
  def AUROC_Unbiased_Subset: Option[Double]
  def Rank_Full: Option[Int]
  def Rank_Unbiased_Subset: Option[Int]

  def RankFinal: Option[Int]
}

case class LDOPAScoreSubmissionInfo(
  Team: String,
  AUPR: Option[Double],
  Rank: Option[Int],
  submissionId: Option[Int],
  submissionName: Option[String],
  featureNum: Option[Int]
) extends SubmissionInfo {
  override val submissionIdInt = submissionId

  override val RankFinal = Rank

  override val AUPR_Unbiased_Subset = None

  override val AUROC_Full = None

  override val AUROC_Unbiased_Subset = None

  override val Rank_Full = None

  override val Rank_Unbiased_Subset = None
}

case class mPowerScoreSubmissionInfo(
  Team: String,
  AUPR_Unbiased_Subset: Option[Double],
  AUROC_Full: Option[Double],
  AUROC_Unbiased_Subset: Option[Double],
  Rank_Full: Option[Int],
  Rank_Unbiased_Subset: Option[Int],
  submissionId: Option[String],
  submissionName: Option[String],
  featureNum: Option[Int]
) extends SubmissionInfo {

  override val submissionIdInt = try {
    submissionId.map(_.toInt)
  } catch {
    case e: NumberFormatException => None
  }

  override val RankFinal = Rank_Unbiased_Subset

  override val Rank = None

  override val AUPR = None
}

case class FeatureInfo(Team: String, SubmissionId: Int, Name: String)

object AggFunction extends Enumeration {
  val max, min, mean = Value
}
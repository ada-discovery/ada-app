package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions}
import controllers.core.WebContext
import models.DataSetFormattersAndIds.FieldIdentity
import models.DataSpaceMetaInfo
import models.security.UserManager
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, AnyContent, Controller, Request}
import security.AdaAuthConfig
import _root_.util.seqFutures
import dataaccess.Criterion._
import models.json.OptionFormat
import play.api.libs.json.Json
import play.api.libs.json._
import reactivemongo.play.json.BSONFormats._

import scala.concurrent.Future

class MPowerChallengeController @Inject()(
    dsaf: DataSetAccessorFactory,
    val userManager: UserManager,
    messagesApi: MessagesApi,
    webJarAssets: WebJarAssets
  ) extends Controller with AdaAuthConfig {

  @Inject var deadbolt: DeadboltActions = _

  private lazy val tremorCorrDsa = dsaf("harvard_ldopa.tremor_correlation").get
  private lazy val tremorScoreBoardDsa = dsaf("harvard_ldopa.score_board_tremor").get

  private lazy val dyskinesiaCorrDsa = dsaf("harvard_ldopa.dyskinesia_correlation").get
  private lazy val dyskinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_dyskinesia").get

  private lazy val bradykinesiaCorrDsa = dsaf("harvard_ldopa.bradykinesia_correlation").get
  private lazy val bradykinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_bradykinesia").get

  private lazy val mPowerCorrDsa = dsaf("mpower_challenge.correlation").get
  private lazy val mPowerScoreBoardDsa = dsaf("mpower_challenge.score_board").get

  private val featureFieldName = "featureName"
  private val absCorrMeanCutoff = 0.4

  case class ScoreSubmissionInfo(
    Team: String,
    AUPR: Double,
    Rank: Option[Int],
    submissionId: Option[Int],
    submissionName: Option[String]
  )

  implicit val scoreSubmissionFormat = Json.format[ScoreSubmissionInfo]

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  private lazy val tremorCorrelationScoresFuture = calcCrossTeamMeanAbsCorrelations(tremorScoreBoardDsa, tremorCorrDsa)

  private lazy val dyskinesiaCorrelationScoresFuture = calcCrossTeamMeanAbsCorrelations(dyskinesiaScoreBoardDsa, dyskinesiaCorrDsa)

  private lazy val bradykinesiaCorrelationScoresFuture = calcCrossTeamMeanAbsCorrelations(bradykinesiaScoreBoardDsa, bradykinesiaCorrDsa)

  private lazy val mPowerCorrelationScoresFuture = calcCrossTeamMeanAbsCorrelations(mPowerScoreBoardDsa, mPowerCorrDsa)

  def tremorNetwork = Action.async { implicit request =>
    showTeamCorrelationNetwork("LDOPA Tremor Subchallenge Team Correlation", tremorScoreBoardDsa, tremorCorrelationScoresFuture)
  }

  def dyskinesiaNetwork = Action.async { implicit request =>
    showTeamCorrelationNetwork("LDOPA Dyskinesia Subchallenge Team Correlation", dyskinesiaScoreBoardDsa, dyskinesiaCorrelationScoresFuture)
  }

  def bradykinesiaNetwork = Action.async { implicit request =>
    showTeamCorrelationNetwork("LDOPA Bradykinesia Subchallenge Team Correlation", bradykinesiaScoreBoardDsa, bradykinesiaCorrelationScoresFuture)
  }

  def mPowerNetwork = Action.async { implicit request =>
    showTeamCorrelationNetwork("mPower Subchallenge Team Correlation", mPowerScoreBoardDsa, mPowerCorrelationScoresFuture)
  }

  def showTeamCorrelationNetwork(
    domainName: String,
    scoreBoardDsa: DataSetAccessor,
    crossTeamCorrelationScoresFuture: Future[Seq[(String, String,  Traversable[Option[Double]])]]
  )(implicit request: Request[AnyContent]) = {
    for {
    // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map(_.as[ScoreSubmissionInfo])
      )

      // cross team mean abs correlations
      crossTeamCorrelationScores <- crossTeamCorrelationScoresFuture
    } yield {
      val teamSubmissionInfos = submissionInfos.groupBy(_.Team).toSeq

      val teamMeanRanks = teamSubmissionInfos.map { case (team, submissions) =>
        val ranks = submissions.flatMap(_.Rank)
        val meanRank = ranks.sum.toDouble / submissions.size
        (team, meanRank)
      }.sortBy((_._2))

      val teamIndexMap = teamMeanRanks.map(_._1).zipWithIndex.toMap

      val maxRank = submissionInfos.flatMap(_.Rank).max
      val nodes = teamSubmissionInfos.flatMap { case (team, submissions) =>
        val ranks = submissions.flatMap(_.Rank)
        val auprs = submissions.map(_.AUPR)

        val meanRank = ranks.sum / submissions.size
        val index = teamIndexMap.get(team).get
        if (meanRank > 0) {
          val data = Json.obj("ranks" -> ranks, "auprs" -> auprs)
          Some(VisNode(index, 5 + (maxRank - meanRank), team, Some(data)))
        } else
          None
      }.sortBy(_.id)

      val edges = crossTeamCorrelationScores.flatMap { case (team1, team2, absCorrMeans) =>
        val index1 = teamIndexMap.get(team1).get
        val index2 = teamIndexMap.get(team2).get
        val definedAbsCorrMeans = absCorrMeans.flatten
        if (definedAbsCorrMeans.nonEmpty) {
          val meanDefinedAbsCorrMean = definedAbsCorrMeans.sum / definedAbsCorrMeans.size
          if (meanDefinedAbsCorrMean > absCorrMeanCutoff) {
            Some(VisEdge(index1, index2, 2 + (meanDefinedAbsCorrMean - absCorrMeanCutoff) * 15, f"$meanDefinedAbsCorrMean%1.2f"))
          } else
            None
        } else
          None
      }
      println("Nodes: " + nodes.size)
      println("Edges: " + edges.size)
      Ok(views.html.networkVis.networkVis(domainName, nodes, edges))
    }
  }

  def calcCrossTeamMeanAbsCorrelations(
    scoreBoardDsa: DataSetAccessor,
    correlationDsa: DataSetAccessor
  ) =
    for {
      // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map(_.as[ScoreSubmissionInfo])
      )

      // get all the feature names
      featureNames <- correlationDsa.fieldRepo.find(Seq(FieldIdentity.name #!= featureFieldName)).map(fields =>
        fields.map(_.name)
      )

      // create a submission id feature names map
      submissionIdFeatureNamesMap = featureNames.map{ featureName =>
        val featureNameParts = featureName.split("-", 2)
        val submissionId = featureNameParts(0).toInt
          (submissionId, featureName)
      }.groupBy(_._1)

      // calculate abs correlation mean between each pair of teams
      crossTeamMeanAbsCorrelations <- calcAbsCorrelationMeansForAllTeams(submissionInfos, submissionIdFeatureNamesMap, correlationDsa)
    } yield
      crossTeamMeanAbsCorrelations

  private def calcAbsCorrelationMeansForAllTeams(
    submissionInfos: Traversable[ScoreSubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[(Int, String)]],
    corrDsa: DataSetAccessor
  ): Future[Seq[(String, String,  Traversable[Option[Double]])]] = {
    def findFeatureNames(submissionInfo: ScoreSubmissionInfo): Traversable[String] =
      submissionInfo.submissionId.map { submissionId =>
        submissionIdFeatureNamesMap.get(submissionId).get.map(_._2)
      }.getOrElse(
        Nil
      )

    val teamSubmissionInfos = submissionInfos.groupBy(_.Team).toSeq.sortBy(_._1)

    seqFutures(teamSubmissionInfos) { case (team1, submissionInfos1) =>
      val allFeatureNames1 = submissionInfos1.map { submissionInfo1 =>
        findFeatureNames(submissionInfo1).toSeq
      }.filter(_.nonEmpty)

      val submissionInfos2 = teamSubmissionInfos.filter { case (team2, subInfos) => team1 < team2}

      seqFutures(submissionInfos2) { case (team2, submissionInfos2) =>
        for {
          meanAbsCorrelations <- Future.sequence(
            for (featureNames1 <- allFeatureNames1; submissionInfo2 <- submissionInfos2) yield {
              val featureNames2 = findFeatureNames(submissionInfo2).toSeq
              extractAbsCorrelationMean(featureNames1, featureNames2, corrDsa)
            }
          )
        } yield
          (team1, team2, meanAbsCorrelations)
      }
    }.map(_.flatten)
  }

  private def extractAbsCorrelationMean(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    corrDsa: DataSetAccessor
  ): Future[Option[Double]] =
    if (featureNames2.nonEmpty) {
      for {
        correlationJsons <- corrDsa.dataSetRepo.find(
          criteria = Seq(featureFieldName #-> featureNames1.map(_.replaceAllLiterally("u002e", "."))),
          projection = featureNames2 :+ featureFieldName
        )
      } yield {
        assert(correlationJsons.size.equals(featureNames1.size), s"The number of correlation rows ${correlationJsons.size} doesn't equal the number of features ${featureNames1.size}: ${featureNames1.mkString(",")}.")

        val correlations = correlationJsons.map { correlationJson =>
          featureNames2.map { featureName2 =>
            (correlationJson \ featureName2).asOpt[Double]
          }
        }

//        extractAbsMean(correlations)
        extractRowColumnMaxes(correlations)
      }
    } else
      Future(None)

  private def extractAbsMean(correlations: Traversable[Seq[Option[Double]]]) = {
    val definedCorrelations = correlations.flatten.flatten
    val absCorrelations = definedCorrelations.map(_.abs)
    Some(absCorrelations.sum / definedCorrelations.size)
  }

  private def extractRowColumnMaxes(correlations: Traversable[Seq[Option[Double]]]) = {
    val absCorrelations = correlations.toSeq.map(_.map(_.map(_.abs)))

    def nonOneMaxes(xs: Traversable[Seq[Option[Double]]]) = {
//      val nonOneXs = xs.map(_.filter(x => x.isDefined && !x.get.equals(1d)))
      val nonOneXs = xs.filterNot(_.contains(Some(1d)))
//      if (xs.size != nonOneXs.size) {
//        println(s"${xs.size} vs ${nonOneXs.size}")
//      }
      nonOneXs.map { row =>
        row match {
          case Nil => None
          case _ => row.max
        }
      }
    }

//    val rowMaxes = nonOneMaxes(absCorrelations)
//    val columnMaxes = nonOneMaxes(absCorrelations.transpose)

    val rowMaxes = absCorrelations.map(_.max)
    val columnMaxes = absCorrelations.transpose.map(_.max)

    val allMaxes = rowMaxes ++ columnMaxes

    Some(allMaxes.flatten.sum / allMaxes.size)
  }
}

case class VisNode(id: Int, size: Int, label: String, data: Option[JsValue])

case class VisEdge(from: Int, to: Int, value: Double, label: String)
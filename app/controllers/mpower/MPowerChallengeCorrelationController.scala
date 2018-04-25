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
  ) extends Controller with FeatureMatrixExtractor {

  private lazy val tremorCorrDsa = dsaf("harvard_ldopa.tremor_correlation").get
  private lazy val tremorScoreBoardDsa = dsaf("harvard_ldopa.score_board_tremor_ext").get
  private lazy val tremorFeatureInfoDsa = dsaf("harvard_ldopa.tremor_feature_info").get

  private lazy val dyskinesiaCorrDsa = dsaf("harvard_ldopa.dyskinesia_correlation").get
  private lazy val dyskinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_dyskinesia_ext").get
  private lazy val dyskinesiaFeatureInfoDsa = dsaf("harvard_ldopa.dyskinesia_feature_info").get

  private lazy val bradykinesiaCorrDsa = dsaf("harvard_ldopa.bradykinesia_correlation").get
  private lazy val bradykinesiaScoreBoardDsa = dsaf("harvard_ldopa.score_board_bradykinesia_ext").get
  private lazy val bradykinesiaFeatureInfoDsa = dsaf("harvard_ldopa.bradykinesia_feature_info").get

  private lazy val mPowerCorrDsa = dsaf("mpower_challenge.correlation").get
  private lazy val mPowerScoreBoardDsa = dsaf("mpower_challenge.score_board_ext").get
  private lazy val mPowerFeatureInfoDsa = dsaf("mpower_challenge.feature_info").get

  private val defaultAbsCorrMeanCutoff = 0.5

  private val featureGroupSize = Some(200)

  private val logger = Logger

  private implicit val ldopaScoreSubmissionFormat = Json.format[LDOPAScoreSubmissionInfo]
  private implicit val mPowerScoreSubmissionFormat = Json.format[mPowerScoreSubmissionInfo]

  private implicit def webContext(implicit request: Request[_]) = {
    implicit val authenticatedRequest = new AuthenticatedRequest(request, None)
    WebContext(messagesApi, webJarAssets)
  }

  // demographic features

  private lazy val tremorDemographicFeaturesFuture =
    groupDemographicFeaturesBySubmission(tremorFeatureInfoDsa)

  private lazy val dyskinesiaDemographicFeaturesFuture =
    groupDemographicFeaturesBySubmission(dyskinesiaFeatureInfoDsa)

  private lazy val bradykinesiaDemographicFeaturesFuture =
    groupDemographicFeaturesBySubmission(bradykinesiaFeatureInfoDsa)

  private lazy val mPowerDemographicFeaturesFuture =
    groupDemographicFeaturesBySubmission(mPowerFeatureInfoDsa)

  // abs correlations

  private lazy val tremorTeamCorrelationScoresFuture =
    calcCrossTeamMeanAbsCorrelations(tremorScoreBoardDsa, tremorCorrDsa)

  private lazy val dyskinesiaTeamCorrelationScoresFuture =
    calcCrossTeamMeanAbsCorrelations(dyskinesiaScoreBoardDsa, dyskinesiaCorrDsa)

  private lazy val bradykinesiaTeamCorrelationScoresFuture =
    calcCrossTeamMeanAbsCorrelations(bradykinesiaScoreBoardDsa, bradykinesiaCorrDsa)

  private lazy val mPowerTeamCorrelationScoresFuture =
    calcCrossTeamMeanAbsCorrelations(mPowerScoreBoardDsa, mPowerCorrDsa)

  private lazy val tremorSubmissionCorrelationScoresFuture =
    calcCrossSubmissionMeanAbsCorrelations(tremorScoreBoardDsa, tremorCorrDsa)

  private lazy val dyskinesiaSubmissionCorrelationScoresFuture =
    calcCrossSubmissionMeanAbsCorrelations(dyskinesiaScoreBoardDsa, dyskinesiaCorrDsa)

  private lazy val bradykinesiaSubmissionCorrelationScoresFuture =
    calcCrossSubmissionMeanAbsCorrelations(bradykinesiaScoreBoardDsa, bradykinesiaCorrDsa)

  private lazy val mPowerSubmissionCorrelationScoresFuture =
    calcCrossSubmissionMeanAbsCorrelations(mPowerScoreBoardDsa, mPowerCorrDsa)

  // abs correlations wo demographics

  private lazy val tremorTeamCorrelationScoresWoDemographicsFuture =
    calcCrossTeamMeanAbsCorrelations(
      tremorScoreBoardDsa, tremorCorrDsa, tremorDemographicFeaturesFuture
    )

  private lazy val dyskinesiaTeamCorrelationScoresWoDemographicsFuture =
    calcCrossTeamMeanAbsCorrelations(
      dyskinesiaScoreBoardDsa, dyskinesiaCorrDsa, dyskinesiaDemographicFeaturesFuture
    )

  private lazy val bradykinesiaTeamCorrelationScoresWoDemographicsFuture =
    calcCrossTeamMeanAbsCorrelations(
      bradykinesiaScoreBoardDsa, bradykinesiaCorrDsa, bradykinesiaDemographicFeaturesFuture
    )

  private lazy val mPowerTeamCorrelationScoresWoDemographicsFuture =
    calcCrossTeamMeanAbsCorrelations(
      mPowerScoreBoardDsa, mPowerCorrDsa, mPowerDemographicFeaturesFuture
    )

  private lazy val tremorSubmissionCorrelationScoresWoDemographicsFuture =
    calcCrossSubmissionMeanAbsCorrelations(
      tremorScoreBoardDsa, tremorCorrDsa, tremorDemographicFeaturesFuture
    )

  private lazy val dyskinesiaSubmissionCorrelationScoresWoDemographicsFuture =
    calcCrossSubmissionMeanAbsCorrelations(
      dyskinesiaScoreBoardDsa, dyskinesiaCorrDsa, dyskinesiaDemographicFeaturesFuture
    )

  private lazy val bradykinesiaSubmissionCorrelationScoresWoDemographicsFuture =
    calcCrossSubmissionMeanAbsCorrelations(
      bradykinesiaScoreBoardDsa, bradykinesiaCorrDsa, bradykinesiaDemographicFeaturesFuture
    )

  private lazy val mPowerSubmissionCorrelationScoresWoDemographicsFuture =
    calcCrossSubmissionMeanAbsCorrelations(
      mPowerScoreBoardDsa, mPowerCorrDsa, mPowerDemographicFeaturesFuture
    )

  def tremorTeamNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        tremorTeamCorrelationScoresFuture
      else
        tremorTeamCorrelationScoresWoDemographicsFuture

    showTeamCorrelationNetwork(
      "LDOPA Tremor Subchallenge Team Correlation",
      tremorScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      tremorDemographicFeaturesFuture
    )
  }

  def dyskinesiaTeamNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        dyskinesiaTeamCorrelationScoresFuture
      else
        dyskinesiaTeamCorrelationScoresWoDemographicsFuture

    showTeamCorrelationNetwork(
      "LDOPA Dyskinesia Subchallenge Team Correlation",
      dyskinesiaScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      dyskinesiaDemographicFeaturesFuture
    )
  }

  def bradykinesiaTeamNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        bradykinesiaTeamCorrelationScoresFuture
      else
        bradykinesiaTeamCorrelationScoresWoDemographicsFuture

    showTeamCorrelationNetwork(
      "LDOPA Bradykinesia Subchallenge Team Correlation",
      bradykinesiaScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      bradykinesiaDemographicFeaturesFuture
    )
  }

  def mPowerTeamNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        mPowerTeamCorrelationScoresFuture
      else
        mPowerTeamCorrelationScoresWoDemographicsFuture

    showTeamCorrelationNetwork(
      "mPower Subchallenge Team Correlation",
      mPowerScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      mPowerDemographicFeaturesFuture
    )
  }

  def tremorSubmissionNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        tremorSubmissionCorrelationScoresFuture
      else
        tremorSubmissionCorrelationScoresWoDemographicsFuture

    showSubmissionCorrelationNetwork(
      "LDOPA Tremor Subchallenge Submission Correlation",
      tremorScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      tremorDemographicFeaturesFuture
    )
  }

  def dyskinesiaSubmissionNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        dyskinesiaSubmissionCorrelationScoresFuture
      else
        dyskinesiaSubmissionCorrelationScoresWoDemographicsFuture

    showSubmissionCorrelationNetwork(
      "LDOPA Dyskinesia Subchallenge Submission Correlation",
      dyskinesiaScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      dyskinesiaDemographicFeaturesFuture
    )
  }

  def bradykinesiaSubmissionNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        bradykinesiaSubmissionCorrelationScoresFuture
      else
        bradykinesiaSubmissionCorrelationScoresWoDemographicsFuture

    showSubmissionCorrelationNetwork(
      "LDOPA Bradykinesia Subchallenge Submission Correlation",
      bradykinesiaScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      bradykinesiaDemographicFeaturesFuture
    )
  }

  def mPowerSubmissionNetwork(
    corrThreshold: Option[Double],
    withDemographics: Boolean
  ) = Action.async { implicit request =>
    val correlationScoresFuture =
      if (withDemographics)
        mPowerSubmissionCorrelationScoresFuture
      else
        mPowerSubmissionCorrelationScoresWoDemographicsFuture

    showSubmissionCorrelationNetwork(
      "mPower Subchallenge Submission Correlation",
      mPowerScoreBoardDsa,
      corrThreshold,
      withDemographics,
      correlationScoresFuture,
      mPowerDemographicFeaturesFuture
    )
  }

  private def showTeamCorrelationNetwork(
    domainName: String,
    scoreBoardDsa: DataSetAccessor,
    corrThreshold: Option[Double],
    withDemographics: Boolean,
    crossTeamCorrelationScoresFuture: Future[Seq[(String, String,  Traversable[(Option[Double], Option[Double])])]],
    submissionIdDemographicFeaturesMapFuture: Future[Map[Int, Traversable[String]]])(
    implicit request: Request[AnyContent]
  ) = {
    val threshold = corrThreshold.getOrElse(defaultAbsCorrMeanCutoff)
    for {
      // cross team mean abs correlations
      crossTeamCorrelationScores <- crossTeamCorrelationScoresFuture

      // demographic features grouped by submission id
      submissionIdDemographicFeaturesMap <- submissionIdDemographicFeaturesMapFuture

      // get all the scored submission infos
      submissionInfos: Traversable[SubmissionInfo] <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map { json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        }
      )
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
            submissionIdDemographicFeaturesMap.get(submissiondId).map(_.size).getOrElse(0)
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

      // create two arrows from and to team 1 and 2
      val correlationScores = crossTeamCorrelationScores.flatMap { case (team1, team2, absCorrMeans) =>
        Seq((team1, team2, absCorrMeans.map(_._1)), (team2, team1, absCorrMeans.map(_._2)))
      }

      val edges = correlationScores.flatMap { case (team1, team2, absCorrMeans) =>
        val index1 = teamIndexMap.get(team1).get
        val index2 = teamIndexMap.get(team2).get
        val definedAbsCorrMeans = absCorrMeans.flatten
        if (definedAbsCorrMeans.nonEmpty) {
          val meanDefinedAbsCorrMean = definedAbsCorrMeans.sum / definedAbsCorrMeans.size
          if (meanDefinedAbsCorrMean > threshold) {
            Some(VisEdge(index1, index2, 2 + (meanDefinedAbsCorrMean) * 10, f"$meanDefinedAbsCorrMean%1.2f"))
          } else
            None
        } else
          None
      }
      println("Nodes: " + nodes.size)
      println("Edges: " + edges.size)
      println(edges.mkString("\n"))
      Ok(views.html.mpowerchallenge.correlationNetwork(domainName, threshold, withDemographics, nodes, edges))
    }
  }

  private def showSubmissionCorrelationNetwork(
    domainName: String,
    scoreBoardDsa: DataSetAccessor,
    corrThreshold: Option[Double],
    withDemographics: Boolean,
    crossSubmissionCorrelationScoresFuture: Future[Seq[(Int, Int, Option[Double], Option[Double])]],
    submissionIdDemographicFeaturesMapFuture: Future[Map[Int, Traversable[String]]])(
    implicit request: Request[AnyContent]
  ) = {
    val threshold = corrThreshold.getOrElse(defaultAbsCorrMeanCutoff)
    for {
      // cross submission mean abs correlations
      crossSubmissionCorrelationScores <- crossSubmissionCorrelationScoresFuture

      // demographic features grouped by submission id
      submissionIdDemographicFeaturesMap <- submissionIdDemographicFeaturesMapFuture

      // get all the scored submission infos
      submissionInfos: Traversable[SubmissionInfo] <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map ( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )
    } yield {
      val sortedSubmissions =  submissionInfos.collect{ case x if x.RankFinal.isDefined && x.submissionIdInt.isDefined => x}.toSeq.sortBy(_.RankFinal.get)
      val submissionIndexMap = sortedSubmissions.map(_.submissionIdInt.get).zipWithIndex.toMap

      val maxRank = submissionInfos.flatMap(_.RankFinal).max
      val nodes = sortedSubmissions.zipWithIndex.map { case (submission, index) =>
        val demographicFeaturesNum = submissionIdDemographicFeaturesMap.get(submission.submissionIdInt.get).map(_.size).getOrElse(0)

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

      // create two arrows from and to submission 1 and 2
      val correlationScores = crossSubmissionCorrelationScores.flatMap { case (submissionId1, submissionId2, absCorrMean1, absCorrMean2) =>
        Seq((submissionId1, submissionId2, absCorrMean1), (submissionId2, submissionId1, absCorrMean2))
      }

      val edges = correlationScores.flatMap { case (submissionId1, submissionId2, absCorrMean) =>
        val index1 = submissionIndexMap.get(submissionId1).get
        val index2 = submissionIndexMap.get(submissionId2).get

        absCorrMean.flatMap { absCorrMean =>
          if (absCorrMean > threshold) {
            Some(VisEdge(index1, index2, 2 + (absCorrMean) * 10, f"$absCorrMean%1.2f"))
          } else
            None
        }
      }
      println("Nodes: " + nodes.size)
      println("Edges: " + edges.size)
      println(edges.mkString("\n"))
      Ok(views.html.mpowerchallenge.correlationNetwork(domainName, threshold, withDemographics, nodes, edges))
    }
  }

  private def calcCrossTeamMeanAbsCorrelations(
    scoreBoardDsa: DataSetAccessor,
    correlationDsa: DataSetAccessor,
    featuresToExcludeFuture: Future[Map[Int, Set[String]]] = Future(Map())
  ) =
    for {
      // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // features to exclude
      submissionFeaturesToExclude <- featuresToExcludeFuture

      // create a submission id feature names map
      submissionIdFeatureNamesMap <- createSubmissionIdFeatureMap(correlationDsa)

      // calculate abs correlation mean between each pair of teams
      crossTeamMeanAbsCorrelations <- {
        val filteredFeatureNamesMap =
          submissionIdFeatureNamesMap.map { case (submissionId, features) =>
            val featuresToExclude = submissionFeaturesToExclude.get(submissionId).getOrElse(Set())
            val newFeatures = features.filterNot(featuresToExclude.contains)

            assert(
              featuresToExclude.size + newFeatures.size == features.size,
              s"The number of features after exclusion is inconsistent." +
              s"There must be some features that do not occur in the original feature set." +
              s"Counts: ${features.size} - ${featuresToExclude.size} != ${newFeatures.size}.\n" +
              s"Features to exclude: ${featuresToExclude.mkString(",")}\n" +
              s"Actual features: ${features.mkString(",")}"
            )

            (submissionId, newFeatures)
          }
        calcAbsCorrelationMeansForAllTeams(submissionInfos, filteredFeatureNamesMap, correlationDsa)
      }
    } yield
      crossTeamMeanAbsCorrelations

  private def calcCrossSubmissionMeanAbsCorrelations(
    scoreBoardDsa: DataSetAccessor,
    correlationDsa: DataSetAccessor,
    featuresToExcludeFuture: Future[Map[Int, Set[String]]] = Future(Map())
  ): Future[Seq[(Int, Int, Option[Double], Option[Double])]] =
    for {
      // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // features to exclude
      submissionFeaturesToExclude <- featuresToExcludeFuture

      // create a submission id feature names map
      submissionIdFeatureNamesMap <- createSubmissionIdFeatureMap(correlationDsa)

      // calculate abs correlation mean between each pair of submissions
      crossSubmissionMeanAbsCorrelations <- {
        val filteredFeatureNamesMap =
          submissionIdFeatureNamesMap.map { case (submissionId, features) =>
            val featuresToExclude = submissionFeaturesToExclude.get(submissionId).getOrElse(Set())
            val newFeatures = features.filterNot(featuresToExclude.contains)

            assert(
              featuresToExclude.size + newFeatures.size == features.size,
              s"The number of features after exclusion is inconsistent." +
              s"There must be some features that do not occur in the original feature set." +
              s"Counts: ${features.size} - ${featuresToExclude.size} != ${newFeatures.size}.\n" +
              s"Features to exclude: ${featuresToExclude.mkString(",")}\n" +
              s"Actual features: ${features.mkString(",")}"
            )

            (submissionId, newFeatures)
          }
        calcAbsCorrelationMeansForAllSubmissions(submissionInfos, filteredFeatureNamesMap, correlationDsa)
      }
    } yield
      crossSubmissionMeanAbsCorrelations

  private def calcAbsCorrelationMeansForAllTeams(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    corrDsa: DataSetAccessor
  ): Future[Seq[(String, String,  Traversable[(Option[Double], Option[Double])])]] = {

    val definedSubmissionInfos = submissionInfos.filter(_.submissionIdInt.isDefined)
    val teamSubmissionInfos = definedSubmissionInfos.groupBy(_.Team).toSeq.sortBy(_._1)

    logger.info(s"Calculating abs correlation means at the team level for ${submissionInfos.size} submissions.")

    seqFutures(teamSubmissionInfos) { case (team1, submissionInfos1) =>
      val submissionInfos2 = teamSubmissionInfos.filter { case (team2, _) => team1 < team2}

      logger.info(s"Calculating abs correlation means for the team ${team1}.")

      seqFutures(submissionInfos2) { case (team2, submissionInfos2) =>
        for {
          meanAbsCorrelations <- Future.sequence(
            for {
              submissionInfo1 <- submissionInfos1
              submissionInfo2 <- submissionInfos2
            } yield {
              val submissionId1 = submissionInfo1.submissionIdInt.get
              val submissionId2 = submissionInfo2.submissionIdInt.get

              calcAbsCorrelationMeansForSubmissionPair(submissionId1, submissionId2, submissionIdFeatureNamesMap, corrDsa).map { case (_, _, aggCorrs1, aggCorrs2) =>
                (aggCorrs1, aggCorrs2)
              }
            }
          )
        } yield
          (team1, team2, meanAbsCorrelations)
      }
    }.map(_.flatten)
  }

  private def calcAbsCorrelationMeansForAllSubmissions(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    corrDsa: DataSetAccessor
  ): Future[Seq[(Int, Int,  Option[Double], Option[Double])]] = {
    val definedSubmissionInfos = submissionInfos.filter(_.submissionIdInt.isDefined)

    logger.info(s"Calculating abs correlation means at the submission level for ${submissionInfos.size} submissions.")

    seqFutures(definedSubmissionInfos) { submissionInfo1 =>
      val submissionId1 = submissionInfo1.submissionIdInt.get
      val submissionInfos2 = definedSubmissionInfos.filter(subInfo => submissionId1 < subInfo.submissionIdInt.get)

      logger.info(s"Calculating abs correlation means for the submission ${submissionId1}.")

      seqFutures(submissionInfos2) { submissionInfo2 =>
        val submissionId2 = submissionInfo2.submissionIdInt.get
        calcAbsCorrelationMeansForSubmissionPair(submissionId1, submissionId2, submissionIdFeatureNamesMap, corrDsa)
      }
    }.map(_.flatten)
  }

  private def calcAbsCorrelationMeansForSubmissionPair(
    submissionId1: Int,
    submissionId2: Int,
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    corrDsa: DataSetAccessor
  ): Future[(Int, Int,  Option[Double], Option[Double])] = {

    // aux function to find feature names for submission ids
    def findFeatureNames(submissionId: Int): Traversable[String] =
      submissionIdFeatureNamesMap.get(submissionId).get

    val featureNames1 = findFeatureNames(submissionId1).toSeq
    val featureNames2 = findFeatureNames(submissionId2).toSeq

    for {
      absMeans <- extractFeatureMatrixAggregates(
        featureNames1, featureNames2, corrDsa, featureGroupSize, aggFun.mean, aggFun.max, Some(_.abs)
      )
    } yield
      (submissionId1, submissionId2, absMeans._1, absMeans._2)
  }

  private def createSubmissionIdFeatureMap(
    correlationDsa: DataSetAccessor
  ): Future[Map[Int, Traversable[String]]] =
    for {
      // get all the feature names
      featureNames <- correlationDsa.fieldRepo.find(Seq(FieldIdentity.name #!= featureFieldName)).map(fields =>
        fields.map(_.name)
      )
    } yield
      // create a submission id feature names map
      featureNames.map{ featureName =>
        val featureNameParts = featureName.split("-", 2)
        val submissionId = featureNameParts(0).toInt
        (submissionId, featureName)
      }.toGroupMap

  private case class FeatureInfo(Team: String, SubmissionId: Int, Name: String)

  private implicit val featureInfoFormat = Json.format[FeatureInfo]

  private def groupDemographicFeaturesBySubmission(
    featureInfoDsa: DataSetAccessor
  ) =
    for {
      categoryField <- featureInfoDsa.fieldRepo.get("Category")

      demographicFeatureInfos <- {
        val field = categoryField.getOrElse(throw new AdaException("Field Category not found"))
        field.numValues.get.find(_._2.equals("demographic")).map(_._1.toInt) match {
          case Some(demographicValue) =>
            featureInfoDsa.dataSetRepo.find(
              criteria = Seq("Category" #== demographicValue)
            ).map(_.map(_.as[FeatureInfo]))

          case None => Future(Nil)
        }
      }
    } yield
      demographicFeatureInfos.groupBy(_.SubmissionId).map{ case (submissionId, values) =>
        // need to add submissionId prefix with "-" becauses that's how the features are stored
        (submissionId, values.map(featureInfo => submissionId + "-" + featureInfo.Name).toSet)
      }
}

case class VisNode(id: Int, size: Int, label: String, data: Option[JsValue])

case class VisEdge(from: Int, to: Int, value: Double, label: String)

trait FeatureMatrixExtractor {

  protected val featureFieldName = "featureName"

  object aggFun {
    val max = aggAux(_.max)(_)
    val min = aggAux(_.min)(_)
    val mean = aggAux(values => values.sum / values.size)(_)

    private def aggAux(
      agg: Seq[Double] => Double)(
      values: Seq[Option[Double]]
    ) =
      values.flatten match {
        case Nil => None
        case flattenedValues => Some(agg(flattenedValues))
      }
  }

  protected def extractFeatureMatrixAggregates(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    groupSize: Option[Int],
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double],
    valueTransform: Option[Double => Double] = None
  ): Future[(Option[Double], Option[Double])] =
    if (featureNames2.nonEmpty) {
      for {
        matrix <- groupSize.map { groupSize =>
          extractFeatureMatrixGrouped(featureNames1, featureNames2, featureMatrixDsa, groupSize, valueTransform)
        }.getOrElse(
          extractFeatureMatrix(featureNames1, featureNames2, featureMatrixDsa, valueTransform)
        )
      } yield {
        extractRowColumnAggregates(matrix.toSeq, aggOut, aggIn)
      }
    } else
      Future((None, None))

  private def extractRowColumnAggregates(
    matrix: Seq[Seq[Option[Double]]],
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double]
  ): (Option[Double], Option[Double]) = {
    def calcAux(m: Seq[Seq[Option[Double]]]) =
      aggOut(m.par.map(aggIn).toList)

    (calcAux(matrix), calcAux(matrix.transpose))
  }

  private def extractFeatureMatrixGrouped(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    groupSize: Int,
    valueTransform: Option[Double => Double] = None
  ): Future[Traversable[Seq[Option[Double]]]] =
    for {
      matrix <-
        seqFutures(featureNames1.grouped(groupSize)) { feat1 =>
          seqFutures(featureNames2.grouped(groupSize)) { feat2 =>
            extractFeatureMatrix(feat1, feat2, featureMatrixDsa, valueTransform)
          }.map { partialColumnValues =>
            val partialColumnValuesSeq = partialColumnValues.map(_.toSeq)
            val rowNum = partialColumnValuesSeq.head.size

            for (rowIndex <- 0 to rowNum - 1) yield partialColumnValuesSeq.flatMap(_(rowIndex))
          }
        }.map(_.flatten)
    } yield
      matrix

  private def extractFeatureMatrix(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    valueTransform: Option[Double => Double] = None
  ): Future[Traversable[Seq[Option[Double]]]] =
    for {
      jsons <- featureMatrixDsa.dataSetRepo.find(
        criteria = Seq(featureFieldName #-> featureNames1.map(_.replaceAllLiterally("u002e", "."))),
        projection = featureNames2 :+ featureFieldName
      )
    } yield {
      assert(
        jsons.size.equals(featureNames1.size),
        s"The number of extracted feature rows ${jsons.size} doesn't equal the number of features ${featureNames1.size}: ${featureNames1.mkString(",")}."
      )

      jsons.map { json =>
        featureNames2.map { featureName2 =>
          (json \ featureName2).asOpt[Double].map { value =>
            valueTransform match {
              case Some(valueTransform) => valueTransform(value)
              case None => value
            }
          }
        }
      }
    }
}


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
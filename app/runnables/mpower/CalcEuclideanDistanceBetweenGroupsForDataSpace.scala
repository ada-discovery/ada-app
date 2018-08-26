package runnables.mpower

import java.nio.charset.StandardCharsets
import javax.inject.Inject

import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import field.FieldTypeHelper
import org.apache.commons.lang3.StringEscapeUtils
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import services.stats.StatsService
import _root_.util.FieldUtil.JsonFieldOps
import org.incal.core.dataaccess.NotEqualsNullCriterion
import services.stats.calc.MatrixCalcHelper
import util.writeByteArrayStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalcEuclideanDistanceBetweenGroupsForDataSpace @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcEuclideanDistanceBetweenGroupsForDataSpaceSpec] with MatrixCalcHelper {

  private val eol = "\n"
  private val headerColumnNames = Seq("dataSetId", "global_avg_dist", "within_group_avg_dist", "between_group_avg_dist", "point_pairs_count", "groups_count")
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  private val logger = Logger

  override def runAsFuture(
    input: CalcEuclideanDistanceBetweenGroupsForDataSpaceSpec
  ) = {
    val unescapedDelimiter = StringEscapeUtils.unescapeJava(input.exportDelimiter)

    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(input.dataSpaceId)

      dataSetIds = dataSpace.map(_.dataSetMetaInfos.map(_.id)).getOrElse(Nil)

      outputs <- util.seqFutures(dataSetIds)(
        calcDistance(input.xFieldName, input.yFieldName, input.groupFieldName, unescapedDelimiter)
      )
    } yield {
      val header = headerColumnNames.mkString(unescapedDelimiter)
      val outputStream = Stream((Seq(header) ++ outputs).mkString(eol).getBytes(StandardCharsets.UTF_8))
      writeByteArrayStream(outputStream, new java.io.File(input.exportFileName))
    }
  }

  private def calcDistance(
    xFieldName: String,
    yFieldName: String,
    groupFieldName: String,
    delimiter: String)(
    dataSetId: String
  ): Future[String] = {
    logger.info(s"Calculating an Euclidean distance between points for the data set $dataSetId using the group field '$groupFieldName'.")
    val dsa = dsaf(dataSetId).get

    for {
      jsons <- dsa.dataSetRepo.find(
        criteria = Seq(NotEqualsNullCriterion(xFieldName), NotEqualsNullCriterion(yFieldName), NotEqualsNullCriterion(groupFieldName)),
        projection = Seq(xFieldName, yFieldName, groupFieldName)
      )

      xField <- dsa.fieldRepo.get(xFieldName)
      yField <- dsa.fieldRepo.get(yFieldName)
      groupField <- dsa.fieldRepo.get(groupFieldName)
    } yield {
      val groupFieldType = ftf(groupField.get.fieldTypeSpec)
      val pointsWithGroup = jsons.map { json =>
        val x = (json \ xFieldName).toOption.map(_.as[Double]).get
        val y = (json \ yFieldName).toOption.map(_.as[Double]).get
        val group = json.toValue(groupFieldName, groupFieldType).get
        (x, y, group)
      }.toSeq

      val groupIndexMap = pointsWithGroup.map(_._3).groupBy(identity).map(_._1).toSeq.zipWithIndex.toMap

      logger.info(s"$pointsWithGroup points with ${groupIndexMap.size} groups prepared for calculation...")

      val startEnds = calcStartEnds(pointsWithGroup.size, Some(16))

      val groupPairDists = startEnds.par.flatMap { case (start, end) =>
        (start to end).map(i =>
          for (j <- 0 until i) yield {
            val point1 = pointsWithGroup(i)
            val point2 = pointsWithGroup(j)

            val xDiff = (point1._1 - point2._1)
            val yDiff = (point1._2 - point2._2)
            val dist = Math.sqrt(xDiff * xDiff + yDiff * yDiff)
            val groupIndex1 = groupIndexMap.get(point1._3).get
            val groupIndex2 = groupIndexMap.get(point2._3).get

            if (groupIndex1 <= groupIndex2)
              ((groupIndex1, groupIndex2), dist)
            else
              ((groupIndex2, groupIndex1), dist)
          })
      }.toList.flatten

      val globalAvgDist = groupPairDists.map(_._2).sum / groupPairDists.size

      val withinSameGroupDists = groupPairDists.filter { case ((groupIndex1, groupIndex2), _) => groupIndex1 == groupIndex2 }
      val betweenDiffGroupsDists = groupPairDists.filter { case ((groupIndex1, groupIndex2), _) => groupIndex1 != groupIndex2 }

      val withinSameGroupAvgDist = withinSameGroupDists.map(_._2).sum / withinSameGroupDists.size
      val betweenDiffGroupsAvgDist = betweenDiffGroupsDists.map(_._2).sum / betweenDiffGroupsDists.size

      Seq(dataSetId, globalAvgDist, withinSameGroupAvgDist, betweenDiffGroupsAvgDist, groupPairDists.size, groupIndexMap.size).mkString(delimiter)
    }
  }

  override def inputType = typeOf[CalcEuclideanDistanceBetweenGroupsForDataSpaceSpec]
}

case class CalcEuclideanDistanceBetweenGroupsForDataSpaceSpec(
  dataSpaceId: BSONObjectID,
  xFieldName: String,
  yFieldName: String,
  groupFieldName: String,
  exportFileName: String,
  exportDelimiter: String
)
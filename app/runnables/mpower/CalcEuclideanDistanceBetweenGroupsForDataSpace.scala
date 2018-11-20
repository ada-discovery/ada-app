package runnables.mpower

import java.nio.charset.StandardCharsets
import javax.inject.Inject

import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import field.FieldTypeHelper
import org.apache.commons.lang3.StringEscapeUtils
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import services.stats.StatsService
import _root_.util.FieldUtil.JsonFieldOps
import org.incal.core.dataaccess.NotEqualsNullCriterion
import org.incal.core.dataaccess.Criterion.Infix
import play.api.libs.json.JsObject
import services.stats.calc.MatrixCalcHelper
import util.writeStringAsStream

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CalcEuclideanDistanceBetweenGroupsForDataSpace @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    statsService: StatsService
  ) extends InputFutureRunnable[CalcEuclideanDistanceBetweenGroupsForDataSpaceSpec] with MatrixCalcHelper {

  private val eol = "\n"
  private val headerColumnNames = Seq("dataSetId", "within_group_avg_dist", "between_group_avg_dist", "groups_count")
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
      val output = (Seq(header) ++ outputs).mkString(eol)
      writeStringAsStream(output, new java.io.File(input.exportFileName))
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

    def points(jsons: Traversable[JsObject]) =
      jsons.map { json =>
        val x = (json \ xFieldName).toOption.map(_.as[Double]).get
        val y = (json \ yFieldName).toOption.map(_.as[Double]).get
        (x, y)
      }

    def dist(
      point1: (Double, Double))(
      point2: (Double, Double)
    ) = {
      val xDiff = (point1._1 - point2._1)
      val yDiff = (point1._2 - point2._2)
      Math.sqrt(xDiff * xDiff + yDiff * yDiff)
    }

    def betweenGroupDist(
      group1Points: Traversable[(Double, Double)],
      group2Points: Traversable[(Double, Double)]
    ): Double = {
      val dists = group1Points.flatMap( point1 => group2Points.map(dist(point1)))

      dists.sum / dists.size
    }

    def withinGroupDist(
      groupPoints: Seq[(Double, Double)]
    ): Double = {
      val dists = (0 until groupPoints.size).flatMap( i =>
        (0 until i).map( j =>
          dist(groupPoints(i))(groupPoints(j))
        )
      )

      dists.sum / dists.size
    }

    for {
      groupField <- dsa.fieldRepo.get(groupFieldName)
      groupFieldType = ftf(groupField.get.fieldTypeSpec)

      groupJsons <- dsa.dataSetRepo.find(
        criteria = Seq(NotEqualsNullCriterion(groupFieldName)),
        projection = Seq(groupFieldName)
      )

      groups = groupJsons.map { groupJson =>
        groupJson.toValue(groupFieldName, groupFieldType).get
      }.toSet.toSeq

      groupDists <- util.seqFutures((0 until groups.size)) { groupIndex1 =>

        for {
          group1Jsons <- dsa.dataSetRepo.find(
            criteria = Seq(NotEqualsNullCriterion(xFieldName), NotEqualsNullCriterion(yFieldName), groupFieldName #== groups(groupIndex1)),
            projection = Seq(xFieldName, yFieldName)
          )

          group1Points = points(group1Jsons)

          betweenGroupDists <- util.seqFutures((0 until groupIndex1)) { groupIndex2 =>

            for {
              group2Jsons <- dsa.dataSetRepo.find(
                criteria = Seq(NotEqualsNullCriterion(xFieldName), NotEqualsNullCriterion(yFieldName), groupFieldName #== groups(groupIndex2)),
                projection = Seq(xFieldName, yFieldName)
              )
            } yield {
              val group2Points = points(group2Jsons)
              betweenGroupDist(group1Points, group2Points)
            }
          }
        } yield {
          (withinGroupDist(group1Points.toSeq), betweenGroupDists)
        }
      }
    } yield {
      val withinGroupDists = groupDists.map(_._1)
      val betweenGroupDists = groupDists.flatMap(_._2)

      val withinGroupAvgDist = withinGroupDists.sum / withinGroupDists.size
      val betweenGroupsAvgDist = betweenGroupDists.sum / betweenGroupDists.size

      Seq(dataSetId, withinGroupAvgDist, betweenGroupsAvgDist, groups.size).mkString(delimiter)
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
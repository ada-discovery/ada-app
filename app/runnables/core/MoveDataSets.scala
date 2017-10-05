package runnables.core

import javax.inject.Inject

import dataaccess.DataSetMetaInfoRepoFactory
import models.AdaException
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID
import runnables.InputFutureRunnable
import util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class MoveDataSets @Inject() (
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory,
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[MoveDataSetsSpec] {

  override def runAsFuture(input: MoveDataSetsSpec) = {
    val dataSetIds =
      (input.suffixFrom, input.suffixTo).zipped.headOption.map { case (from, to) =>
        (from to to).map(input.dataSetId + _)
      }.getOrElse(
        Seq(input.dataSetId)
      )

    for {
      _ <- seqFutures(dataSetIds) { move(_, input.newDataSpaceId) }
    } yield
      ()
  }

  private def move(dataSetId: String, newDataSpaceId: BSONObjectID): Future[Unit] = {
    val dsa = dsaf(dataSetId).getOrElse(
      throw new AdaException(s"Data set $dataSetId not found.")
    )

    for {
      metaInfo <- dsa.metaInfo
      // delete meta info at the old data space
      _ <- {
        val repo = dataSetMetaInfoRepoFactory(metaInfo.dataSpaceId)
        repo.delete(metaInfo._id.get)
      }

      // save it to a new one
      _ <- {
        val repo = dataSetMetaInfoRepoFactory(newDataSpaceId)
        repo.save(metaInfo.copy(dataSpaceId = newDataSpaceId))
      }
    } yield
      ()
  }

  override def inputType = typeOf[MoveDataSetsSpec]
}

case class MoveDataSetsSpec(newDataSpaceId: BSONObjectID, dataSetId: String, suffixFrom: Option[Int], suffixTo: Option[Int])
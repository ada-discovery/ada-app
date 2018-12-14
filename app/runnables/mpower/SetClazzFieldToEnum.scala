package runnables.mpower

import javax.inject.Inject

import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models.FieldTypeId
import persistence.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import org.incal.core.InputFutureRunnable
import org.incal.core.util.seqFutures

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SetClazzFieldToEnum @Inject()(
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnable[SetClazzFieldToEnumSpec] {

  private val logger = Logger

  override def runAsFuture(input: SetClazzFieldToEnumSpec) = {
    for {
      dataSpace <- dataSpaceMetaInfoRepo.get(BSONObjectID.parse(input.dataSpaceId).get).map(_.get)

      _ <- seqFutures(dataSpace.dataSetMetaInfos) { dataSetMetaInfo =>
        val dataSetId = dataSetMetaInfo.id
        val dsa = dsaf(dataSetId).get

        val index = dataSetId.indexOf("kmeans_")
        if (index > 0) {
          val k = dataSetId.substring(index + 7, dataSetId.length).split('_').head.toInt

          logger.info(s"Setting clazz field to enun for $dataSetId and k $k.")

          dsa.fieldRepo.get("clazz").flatMap {
            _ match {
              case Some(clazzField) =>
                val enumValues = for (i <- 1 to k) yield (i.toString -> i.toString)
                val newField = clazzField.copy(fieldType = FieldTypeId.Enum, numValues = Some(enumValues.toMap))
                dsa.fieldRepo.update(newField)
              case None =>
                Future("")
            }
          }
        } else
          Future(())
      }
    } yield
      ()
  }

  override def inputType = typeOf[SetClazzFieldToEnumSpec]
}

case class SetClazzFieldToEnumSpec(
  dataSpaceId: String
)
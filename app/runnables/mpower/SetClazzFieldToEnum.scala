package runnables.mpower

import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import org.ada.server.models.FieldTypeId
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import org.incal.core.runnables.{InputFutureRunnable, InputFutureRunnableExt}
import org.incal.core.util.seqFutures

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SetClazzFieldToEnum @Inject()(
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dsaf: DataSetAccessorFactory
  ) extends InputFutureRunnableExt[SetClazzFieldToEnumSpec] {

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
                val newField = clazzField.copy(fieldType = FieldTypeId.Enum, enumValues = enumValues.toMap)
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
}

case class SetClazzFieldToEnumSpec(
  dataSpaceId: String
)
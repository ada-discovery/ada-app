package services

import com.google.inject.Inject
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.field.FieldUtil
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion._
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait SampleRequestService {
  def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    columNames: Seq[String] = Nil
  ): Future[Any]

  def sendToRems(
    csv: Any
  )
}

class SampleRequestServiceImpl @Inject() (
  dsaf: DataSetAccessorFactory
) extends SampleRequestService {

  override def createCsv(
    dataSetId: String,
    filter: Seq[FilterCondition] = Nil,
    fieldNames: Seq[String] = Nil
  ): Future[Any] = {
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset '$dataSetId' does not exist."))
    val fieldRepo = dsa.fieldRepo
    val dataSetRepo = dsa.dataSetRepo

    for {
      fields <- fieldRepo.find(Vector(FieldIdentity.name #-> fieldNames))
      criteria <- FieldUtil.toDataSetCriteria(fieldRepo, filter)
      items <- dataSetRepo.find(criteria)
    } yield {
      val fieldNameTypes = fields.map(_.toNamedTypeAny).toSeq
      val fieldNameValues = fieldNameTypes map { fieldNameType =>
        val values = items.map(_.toValue(fieldNameType)).toSeq
        (fieldNameType._1, values)
      } toMap
      // TODO: return suitable format
    }
  }

  override def sendToRems(csv: Any): Unit = {

  }

}
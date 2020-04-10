package runnables.convince

import com.google.inject.Inject
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.runnables.FutureRunnable
import org.incal.core.dataaccess.Criterion._

import scala.concurrent.ExecutionContext.Implicits.global
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity


// FIXME adapt to real data
class NotifyCliniciansIfDonorPositive @Inject() (
  dsaf: DataSetAccessorFactory
) extends FutureRunnable {
  private val dataSetId = "open.iris"

  override def runAsFuture = {
    val dsa = dsaf(dataSetId).getOrElse(throw new IllegalArgumentException(s"Dataset '$dataSetId' does not exist."))
    val fieldRepo = dsa.fieldRepo
    val dataSetRepo = dsa.dataSetRepo

    for {
      fields <- fieldRepo.find(Vector(FieldIdentity.name #-> Vector("petal_length", "petal_width")))
      items <- dataSetRepo.find(Vector("petal_length" #> "1"))
    } yield {
      val fieldNameTypes = fields.map(_.toNamedTypeAny).toSeq
      val fieldNameValues = fieldNameTypes map { fieldNameType =>
        val values = items.map(_.toValue(fieldNameType)).toSeq
        (fieldNameType._1, values)
      } toMap

      println(fieldNameValues("petal_length"))
    }
  }
}

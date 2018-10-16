package runnables.core

import javax.inject.Inject

import models.DataSetFormattersAndIds.FieldIdentity
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory
import org.incal.core.dataaccess.Criterion.Infix

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RenameFields @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[RenameFieldsSpec] {

  override def runAsFuture(
    input: RenameFieldsSpec
  ) = {
    val dsa = dsaf(input.dataSetId).get

    val nameLabelMap = input.fieldNameLabels.grouped(2).toSeq.map(seq => (seq(0), seq(1))).toMap
    val names = nameLabelMap.map(_._1).toSeq

    for {
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> names))

      _ <- {
        val newLabelFields = fields.map { field =>
          val newLabel = nameLabelMap.get(field.name).get
          field.copy(label = Some(newLabel))
        }
        dsa.fieldRepo.update(newLabelFields)
      }
    } yield
      ()
  }

  override def inputType = typeOf[RenameFieldsSpec]
}

case class RenameFieldsSpec(
  dataSetId: String,
  fieldNameLabels: Seq[String]
)
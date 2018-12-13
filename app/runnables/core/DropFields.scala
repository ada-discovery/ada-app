package runnables.core

import javax.inject.Inject

import models.ml.{DerivedDataSetSpec, DropFieldsSpec, RenameFieldsSpec}
import org.incal.core.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class DropFields @Inject() (dataSetService: DataSetService) extends InputFutureRunnable[DropFieldsSpec] {

  override def runAsFuture(input: DropFieldsSpec) =
    dataSetService.dropFields(input)

  override def inputType = typeOf[DropFieldsSpec]
}
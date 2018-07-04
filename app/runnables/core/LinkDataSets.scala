package runnables.core

import javax.inject.Inject

import scala.reflect.runtime.universe.typeOf
import models.ml.DataSetLinkSpec
import runnables.InputFutureRunnable
import services.DataSetService

class LinkDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[DataSetLinkSpec] {

  override def runAsFuture(input: DataSetLinkSpec) = dataSetService.linkDataSets(input)

  override def inputType = typeOf[DataSetLinkSpec]
}

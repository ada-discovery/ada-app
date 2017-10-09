package runnables.core

import javax.inject.Inject

import scala.reflect.runtime.universe.typeOf
import models.ml.DataSetLink
import runnables.InputFutureRunnable
import services.DataSetService

class LinkDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[DataSetLink] {

  override def runAsFuture(input: DataSetLink) = dataSetService.linkDataSets(input)

  override def inputType = typeOf[DataSetLink]
}

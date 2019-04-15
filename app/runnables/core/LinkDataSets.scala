package runnables.core

import javax.inject.Inject

import scala.reflect.runtime.universe.typeOf
import models.DataSetLinkSpec
import org.incal.core.InputFutureRunnable
import services.DataSetService

class LinkDataSets @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[DataSetLinkSpec] {

  override def runAsFuture(input: DataSetLinkSpec) = dataSetService.linkDataSets(input)

  override def inputType = typeOf[DataSetLinkSpec]
}

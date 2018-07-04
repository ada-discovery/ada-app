package runnables.core

import javax.inject.Inject

import models.ml.SelfLinkSpec
import runnables.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class SelfLink @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[SelfLinkSpec] {

  override def runAsFuture(input: SelfLinkSpec) = dataSetService.selfLink(input)

  override def inputType = typeOf[SelfLinkSpec]
}

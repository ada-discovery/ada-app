package runnables.dl4j

import org.incal.core.runnables.InputRunnable
import org.incal.dl4j.{DL4JHelper, TimeSeriesClassificationSpec}

import scala.reflect.runtime.universe.typeOf

class RunDL4JTimeSeriesClassification extends InputRunnable[TimeSeriesClassificationSpec] with DL4JHelper {

  override def inputType = typeOf[TimeSeriesClassificationSpec]
}
package runnables.dl4j

import org.incal.core.runnables.InputRunnable
import org.incal.dl4j.{DL4JHelper, TimeSeriesClassificationWithRandomSplitSpec}

import scala.reflect.runtime.universe.typeOf

class RunDL4JTimeSeriesClassificationWithRandomSplit extends InputRunnable[TimeSeriesClassificationWithRandomSplitSpec] with DL4JHelper {

  override def inputType = typeOf[TimeSeriesClassificationWithRandomSplitSpec]
}
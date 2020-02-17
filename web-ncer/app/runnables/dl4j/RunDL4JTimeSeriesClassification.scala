package runnables.dl4j

import org.incal.core.runnables.InputRunnableExt
import org.incal.dl4j.{DL4JHelper, TimeSeriesClassificationSpec}

class RunDL4JTimeSeriesClassification extends InputRunnableExt[TimeSeriesClassificationSpec] with DL4JHelper
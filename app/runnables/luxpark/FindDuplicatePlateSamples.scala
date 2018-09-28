package runnables.luxpark

import javax.inject.Inject

import org.incal.core.FutureRunnable
import runnables.core.{FindDuplicates, FindDuplicatesSpec}

class FindDuplicatePlateSamples @Inject()(findDuplicates: FindDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.plate_sample_with_subject_oct_17"
  private val sampleIdField = "SampleId"

  override def runAsFuture =
    findDuplicates.runAsFuture(FindDuplicatesSpec(dataSetId, Seq(sampleIdField)))
}
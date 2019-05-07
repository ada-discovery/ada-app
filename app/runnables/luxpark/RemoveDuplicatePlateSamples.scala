package runnables.luxpark

import javax.inject.Inject

import org.incal.core.runnables.FutureRunnable
import runnables.core.{RemoveDuplicates, RemoveDuplicatesSpec}

class RemoveDuplicatePlateSamples @Inject()(removeDuplicates: RemoveDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.plate_sample_with_subject_oct_17"
  private val sampleIdField = "SampleId"

  override def runAsFuture =
    removeDuplicates.runAsFuture(RemoveDuplicatesSpec(dataSetId, Seq(sampleIdField)))
}
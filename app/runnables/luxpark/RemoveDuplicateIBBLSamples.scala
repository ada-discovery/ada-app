package runnables.luxpark

import javax.inject.Inject

import org.incal.core.FutureRunnable
import runnables.core.{RemoveDuplicates, RemoveDuplicatesSpec}

class RemoveDuplicateIBBLSamples @Inject()(removeDuplicates: RemoveDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.ibbl_biosamples"
  private val sampleIdField = "sampleid"

  override def runAsFuture =
    removeDuplicates.runAsFuture(RemoveDuplicatesSpec(dataSetId, Seq(sampleIdField)))
}
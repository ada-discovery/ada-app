package runnables.luxpark

import javax.inject.Inject

import runnables.FutureRunnable
import runnables.core.{FindDuplicates, FindDuplicatesSpec}

class FindDuplicateIBBLSamples @Inject()(findDuplicates: FindDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.ibbl_biosamples"
  private val sampleIdField = "sampleid"

  override def runAsFuture =
    findDuplicates.runAsFuture(FindDuplicatesSpec(dataSetId, Seq(sampleIdField)))
}
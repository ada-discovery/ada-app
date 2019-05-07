package runnables.luxpark

import javax.inject.Inject

import org.incal.core.runnables.FutureRunnable
import runnables.core.{RemoveDuplicates, RemoveDuplicatesSpec}

class RemoveDuplicateClinicalVisits @Inject()(removeDuplicates: RemoveDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.clinical"

  private val subjectIdField = "cdisc_dm_usubjd"
  private val visitField = "redcap_event_name"

  override def runAsFuture =
    removeDuplicates.runAsFuture(RemoveDuplicatesSpec(dataSetId, Seq(subjectIdField, visitField)))
}
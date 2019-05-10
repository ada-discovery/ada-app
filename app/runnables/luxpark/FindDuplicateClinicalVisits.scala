package runnables.luxpark

import javax.inject.Inject

import org.incal.core.runnables.FutureRunnable
import org.ada.server.runnables.core.{FindDuplicates, FindDuplicatesSpec}

class FindDuplicateClinicalVisits @Inject()(findDuplicates: FindDuplicates) extends FutureRunnable {

  private val dataSetId = "lux_park.clinical"

  private val subjectIdField = "cdisc_dm_usubjd"
  private val visitField = "redcap_event_name"

  override def runAsFuture =
    findDuplicates.runAsFuture(FindDuplicatesSpec(dataSetId, Seq(subjectIdField, visitField)))
}
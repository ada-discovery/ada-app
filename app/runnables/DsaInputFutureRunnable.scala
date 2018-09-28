package runnables

import javax.inject.Inject

import models.AdaException
import org.incal.core.InputFutureRunnable
import persistence.dataset.DataSetAccessorFactory

trait DsaInputFutureRunnable[I] extends InputFutureRunnable[I] {

  @Inject var dsaf: DataSetAccessorFactory = _

  protected def dsa(dataSetId: String) = dsaf(dataSetId).getOrElse(
    throw new AdaException(s"Data set id ${dataSetId} not found.")
  )

  protected def dataSetRepo(dataSetId: String) = dsa(dataSetId).dataSetRepo
}
package runnables.core

import javax.inject.Inject

import models.StorageType
import runnables.InputFutureRunnable
import services.DataSetService

import scala.reflect.runtime.universe.typeOf

class CopyToNewStorage @Inject()(dataSetService: DataSetService) extends InputFutureRunnable[CopyToNewStorageSpec]{

  override def runAsFuture(input: CopyToNewStorageSpec) =
    dataSetService.copyToNewStorage(
      input.dataSetId,
      input.groupSize,
      input.parallelism,
      input.backpressureBufferSize,
      input.saveDeltaOnly,
      input.targetStorageType
    )

  override def inputType = typeOf[CopyToNewStorageSpec]
}

case class CopyToNewStorageSpec(
  dataSetId: String,
  groupSize: Int,
  parallelism: Int,
  backpressureBufferSize: Int,
  saveDeltaOnly: Boolean,
  targetStorageType: StorageType.Value
)
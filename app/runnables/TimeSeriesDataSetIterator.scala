package runnables

import java.util

import org.datavec.api.records.reader.{RecordReader, SequenceRecordReader}
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator
import org.deeplearning4j.datasets.datavec.RecordReaderMultiDataSetIterator.AlignmentMode
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.dataset.api.{DataSetPreProcessor, MultiDataSet}
import org.nd4j.linalg.indexing.INDArrayIndex

private class TimeSeriesDataSetIterator(
  batchSize: Int,
  inputReader: SequenceRecordReader,
  labelReader: RecordReader
) extends DataSetIterator {

  private val mdsIterator: RecordReaderMultiDataSetIterator = new RecordReaderMultiDataSetIterator.Builder(batchSize)
    .sequenceAlignmentMode(AlignmentMode.EQUAL_LENGTH)
    .addSequenceReader("input", inputReader)
    .addReader("output", labelReader)
    .addInput("input")
    .addOutputOneHot("output", 0, 2)
    .build()

  private var preProcessor: DataSetPreProcessor = null

  protected var last: DataSet = null
  protected var useCurrent = false

  override def getPreProcessor = preProcessor

  override def setPreProcessor(preProcessor: DataSetPreProcessor) =
    this.preProcessor = preProcessor

  override def getLabels: util.List[String] =
    labelReader.getLabels

  override def inputColumns: Int =
    if (last == null) {
      val nextDS = next()
      last = nextDS
      useCurrent = true
      nextDS.numInputs
    } else
      last.numInputs

  override def totalOutcomes: Int =
    if (last == null) {
      val nextDS = next()
      last = nextDS
      useCurrent = true
      nextDS.numOutcomes
    } else
      last.numOutcomes

  override def batch =
    batchSize

  override def resetSupported =
    mdsIterator.resetSupported()

  override def asyncSupported = true // ??

  override def reset = {
    mdsIterator.reset()
    last = null
    useCurrent = false
  }

  override def hasNext =
    mdsIterator.hasNext

  override def next = next(batchSize)

  override def next(num: Int): DataSet = {
    val ds = if (useCurrent) {useCurrent = false; last} else mdsToDataSet(mdsIterator.next(num))

    if (preProcessor != null) preProcessor.preProcess(ds)
    ds
  }

  private def mdsToDataSet(mds: MultiDataSet): DataSet = {
    var f: INDArray = getOrNull(mds.getFeatures, 0)
    var fm: INDArray = getOrNull(mds.getFeaturesMaskArrays, 0)

    // [minibatchSize, channels, time series length] -> [minibatchSize, layerInputDepth, inputHeight, inputWidth]
    // need [minibatchSize, 1, channels, time series length]

    val oldShape = f.shape()
    println(util.Arrays.toString(oldShape))
    val newF = f.reshape(oldShape(0), 1, oldShape(1), oldShape(2)).swapAxes(2, 3)

    println(util.Arrays.toString(newF.shape()))

    val l = getOrNull(mds.getLabels, 0)
    val lm = getOrNull(mds.getLabelsMaskArrays, 0)
    new DataSet(newF, l, fm, lm)
  }

  private def getOrNull(arr: Array[INDArray], idx: Int) =
    if (arr == null || arr.length == 0) null else arr(idx)
}

object TimeSeriesDataSetIterator {

  def apply(
    batchSize: Int,
    inputReader: SequenceRecordReader,
    labelReader: RecordReader
  ): DataSetIterator = new TimeSeriesDataSetIterator(batchSize, inputReader, labelReader)
}
package runnables.dl4j

import controllers.core.GenericMapping
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration
import org.deeplearning4j.nn.graph.ComputationGraph
import org.incal.core.InputRunnable
import org.incal.core.util.writeStringAsStream
import org.nd4j.evaluation.EvaluationAveraging
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import play.api.Logger

trait DL4JHelper {

  this: InputRunnable[_] =>

  protected val log = Logger

  private val outputEvalHeader = Seq(
    "epoch", "trainAccuracy", "trainMacroF1", "trainMicroF1", "validationAccuracy", "validationMacroF1", "validationMicroF1"
  ).mkString(", ")

  private val dateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

  protected def launchAndReportResults(config: ComputationGraphConfiguration, trainData: DataSetIterator, validationData: DataSetIterator, numEpochs: Int, exportDir: String, input: Any) = {
    val startTime = new java.util.Date()

    // create a model and initialize
    val model = new ComputationGraph(config)
    model.init()

    // Input as string
    val mapping = GenericMapping[Any](inputType)
    val inputParams = mapping.unbind(input).map { case (a, b) => s"$a: $b" }.toSeq.sorted
    log.info("Input: " + inputParams.mkString(", "))

    // Train the model
    log.info("Training the model....")

    val trainValidationEvals = for (i <- 1 to numEpochs) yield {
      model.fit(trainData)
      log.info(s"*** Completed epoch $i ***")

      val trainEval: Evaluation = model.evaluate(trainData)
      log.info("Train accuracy     : " + trainEval.accuracy)

      val validationEval: Evaluation = model.evaluate(validationData)
      log.info("Validation accuracy: " + validationEval.accuracy)

      (trainEval, validationEval)
    }
    log.info("*** Training finished ***")

    // Write input params to a file
    val startTimeString = dateTimeFormat.format(startTime)
    val paramsFileName = s"CNN_DL4J-$startTimeString-input"

    writeStringAsStream(
      inputParams.mkString("\n"),
      new java.io.File(exportDir + "/" + paramsFileName)
    )

    // Write selected evaluation metrics to a file
    val evalOutputFileName = s"CNN_DL4J-$startTimeString-evals.csv"

    val evalOutput = trainValidationEvals.zipWithIndex.map { case ((trainEval, validationEval), index) =>
      val trainAccuracy = trainEval.accuracy
      val trainMacroF1 = trainEval.f1(EvaluationAveraging.Macro)
      val trainMicroF1 = trainEval.f1(EvaluationAveraging.Micro)

      val validationAccuracy = validationEval.accuracy
      val validationMacroF1 = validationEval.f1(EvaluationAveraging.Macro)
      val validationMicroF1 = validationEval.f1(EvaluationAveraging.Micro)

      Seq((index + 1).toInt, trainAccuracy, trainMacroF1, trainMicroF1, validationAccuracy, validationMacroF1, validationMicroF1).mkString(", ")
    }.mkString("\n")

    writeStringAsStream(
      outputEvalHeader + "\n" + evalOutput,
      new java.io.File(exportDir + "/" + evalOutputFileName)
    )

    // Write the full final evaluation stats to a file
    val lastFullEvalOutputFileName = s"CNN_DL4J-$startTimeString-lasteval"

    val (lastTrainingEval, lastValidationEval) = trainValidationEvals.last

    writeStringAsStream(
      "Training:" +
        lastTrainingEval.stats + "\n\n" +
        "Validation:" +
        lastValidationEval.stats,
      new java.io.File(exportDir + "/" + lastFullEvalOutputFileName)
    )
  }
}

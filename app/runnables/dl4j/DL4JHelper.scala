package runnables.dl4j

import org.ada.web.controllers.core.GenericMapping
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration
import org.deeplearning4j.nn.graph.ComputationGraph
import org.incal.core.runnables.InputRunnable
import org.incal.core.util.writeStringAsStream
import org.nd4j.evaluation.EvaluationAveraging
import org.nd4j.evaluation.classification.{Evaluation, ROC}
import org.nd4j.evaluation.curves.{PrecisionRecallCurve, RocCurve}
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import play.api.Logger

trait DL4JHelper {

  this: InputRunnable[_] =>

  protected val log = Logger

  private val outputEvalHeader = Seq(
    "epoch", "trainingAccuracy", "trainingMacroF1", "trainingMicroF1", "validationAccuracy", "validationMacroF1", "validationMicroF1"
  ).mkString(", ")

  private val binaryOutputEvalHeader = Seq(
    "epoch", "trainingAccuracy", "trainingF1Class0", "trainingF1Class1", "trainingAUROC", "trainingAUPR", "validationAccuracy", "validationF1Class0", "validationF1Class1", "validationAUROC", "validationAUPR"
  ).mkString(", ")

  private val dateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

  protected def launchAndReportResults(
    config: ComputationGraphConfiguration,
    trainData: DataSetIterator,
    validationData: DataSetIterator,
    numEpochs: Int,
    outputNum: Int,
    exportDir: String,
    input: Any
  ) = {
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

    // Aux function to evaluate the performance
    def evaluate(
      data: DataSetIterator,
      dataSetName: String
    ): (Evaluation, Option[RocCurve], Option[PrecisionRecallCurve]) =
      if (outputNum == 2) {
        val eval = new Evaluation()
        val roc = new ROC()
        model.doEvaluation(data, eval, roc)
        val rocCurve = roc.getRocCurve
        val prCurve = roc.getPrecisionRecallCurve

        log.info(s"$dataSetName accuracy: ${eval.accuracy()}")
        log.info(s"$dataSetName AUROC   : ${rocCurve.calculateAUC()}")
        log.info(s"$dataSetName AUPR    : ${prCurve.calculateAUPRC()}")

        (eval, Some(rocCurve), Some(prCurve))
      } else {
        val eval: Evaluation = model.evaluate(data)

        log.info(s"$dataSetName accuracy: ${eval.accuracy()}")

        (eval, None, None)
      }

    val trainValidationEvals = for (i <- 1 to numEpochs) yield {
      model.fit(trainData)
      log.info(s"*** Completed epoch $i ***")

      val trainingEval = evaluate(trainData, "Training  ")
      val validationEval = evaluate(validationData, "Validation")
      (trainingEval, validationEval)
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

    val evalOutput = trainValidationEvals.zipWithIndex.map { case (((trainingEval, trainingRocCurve, trainingPrCurve), (validationEval, validationRocCurve, validationPrCurve)), index) =>
      val trainingAccuracy = trainingEval.accuracy

      val validationAccuracy = validationEval.accuracy

      if (outputNum == 2) {
        val trainingF1Class0 = trainingEval.f1(0)
        val trainingF1Class1 = trainingEval.f1(1)
        val trainingAUROC = trainingRocCurve.map(_.calculateAUC().toString).getOrElse("")
        val trainingAUPR = trainingPrCurve.map(_.calculateAUPRC().toString).getOrElse("")

        val validationF1Class0 = validationEval.f1(0)
        val validationF1Class1 = validationEval.f1(1)
        val validationAUROC = validationRocCurve.map(_.calculateAUC().toString).getOrElse("")
        val validationAUPR = validationPrCurve.map(_.calculateAUPRC().toString).getOrElse("")

        Seq(index + 1, trainingAccuracy, trainingF1Class0, trainingF1Class1, trainingAUROC, trainingAUPR, validationAccuracy, validationF1Class0, validationF1Class1, validationAUROC, validationAUPR).mkString(", ")
      } else {
        val trainingMacroF1 = trainingEval.f1(EvaluationAveraging.Macro)
        val trainingMicroF1 = trainingEval.f1(EvaluationAveraging.Micro)

        val validationMacroF1 = validationEval.f1(EvaluationAveraging.Macro)
        val validationMicroF1 = validationEval.f1(EvaluationAveraging.Micro)

        Seq(index + 1, trainingAccuracy, trainingMacroF1, trainingMicroF1, validationAccuracy, validationMacroF1, validationMicroF1).mkString(", ")
      }
    }.mkString("\n")

    val evalHeader = if (outputNum == 2) binaryOutputEvalHeader else outputEvalHeader

    writeStringAsStream(
      evalHeader + "\n" + evalOutput,
      new java.io.File(exportDir + "/" + evalOutputFileName)
    )

    // Write the full final evaluation stats to a file
    val lastFullEvalOutputFileName = s"CNN_DL4J-$startTimeString-lasteval"

    val ((lastTrainingEval, lastTrainingRocCurve, lastTrainingPrCurve), (lastValidationEval, lastValidationRocCurve, lastValidationPrCurve)) = trainValidationEvals.last

    def rocAsString(curve: Option[RocCurve]): String =
      curve.map(curve => "\n\nFPR: " + curve.getFpr.mkString(",") + "\n" + "TPR: " + curve.getTpr.mkString(",") + "\n\n").getOrElse("")

    def prAsString(curve: Option[PrecisionRecallCurve]): String =
      curve.map(curve => "Precision: " + curve.getPrecision.mkString(",") + "\n" + "Recall: " + curve.getRecall.mkString(",") + "\n").getOrElse("")

    writeStringAsStream(
      "Training:" +
      lastTrainingEval.stats + rocAsString(lastTrainingRocCurve) + prAsString(lastTrainingPrCurve) + "\n\n" +
      "Validation:" +
      lastValidationEval.stats + rocAsString(lastValidationRocCurve) + prAsString(lastValidationPrCurve),
      new java.io.File(exportDir + "/" + lastFullEvalOutputFileName)
    )
  }
}

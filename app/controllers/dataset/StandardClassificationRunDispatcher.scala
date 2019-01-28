package controllers.dataset

import javax.inject.Inject

import org.incal.play.controllers.SecureControllerDispatcher
import org.incal.core.FilterCondition
import org.incal.spark_ml.models.setting.ClassificationRunSpec
import reactivemongo.bson.BSONObjectID
import org.incal.play.security.SecurityRole
import models.security.DataSetPermission

class StandardClassificationRunDispatcher @Inject()(
  val dscf: DataSetControllerFactory,
  factory: StandardClassificationRunControllerFactory
) extends MLRunDispatcher[StandardClassificationRunController](ControllerName.classificationRun)
    with StandardClassificationRunController {

  override def controllerFactory = factory(_)

  override def launch(
    runSpec: ClassificationRunSpec,
    saveResults: Boolean,
    saveBinCurves: Boolean
  ) = dispatch(_.launch(runSpec, saveResults, saveBinCurves))

  override def selectFeaturesAsAnovaChiSquare(
    inputFieldNames: Seq[String],
    outputFieldName: String,
    filterId: Option[BSONObjectID],
    featuresToSelectNum: Int
  ) = dispatch(_.selectFeaturesAsAnovaChiSquare(inputFieldNames, outputFieldName, filterId, featuresToSelectNum))
}
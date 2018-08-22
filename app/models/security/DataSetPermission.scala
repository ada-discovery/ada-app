package models.security

import be.objectify.deadbolt.scala.models.Permission
import controllers.dataset.ControllerName

object DataSetPermission {

  def apply(
    dataSetId: String,
    controllerName: ControllerName.Value,
    actionName: String
  ) = "\\bDS:" + dataSetId.replaceAll("\\.","\\\\.") + "(\\." + controllerName.toString + "(\\." + actionName + ")?)?\\b"
}
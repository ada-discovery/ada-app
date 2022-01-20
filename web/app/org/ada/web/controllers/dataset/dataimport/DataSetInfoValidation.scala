package org.ada.web.controllers.dataset.dataimport

import org.ada.server.models.DataSetInfo
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object DataSetInfoValidation {

  val dataSetJoinIdNameConstraint: Constraint[DataSetInfo] = Constraint("constraints.dataSetJoinIdName")({
    dataSetInfo =>
      if (dataSetInfo.dataSetType.toString.nonEmpty && dataSetInfo.dataSetJoinIdName.isEmpty)
        Invalid(Seq(ValidationError("Data Set Join Field must not be empty '[None]'")))
      else
        Valid
  })
}

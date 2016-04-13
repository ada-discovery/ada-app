package controllers.dataset.denopa

import javax.inject.Inject

import controllers.dataset.DataSetControllerImpl
import models.DataSetId._
import persistence.RepoTypes._
import persistence.dataset.DataSetAccessorFactory

class DenopaSecondvisitController @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetMetaInfoRepo: DataSetMetaInfoRepo
  ) extends DataSetControllerImpl(denopa_secondvisit, dsaf, dataSetMetaInfoRepo)
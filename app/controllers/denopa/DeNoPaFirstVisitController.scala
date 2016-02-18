package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.{StudyRouter, DataSetRouter}
import persistence.DictionaryFieldRepo

class DeNoPaFirstVisitController @Inject() (
    @Named("DeNoPaFirstVisitDictionaryRepo") dictionaryRepo: DictionaryFieldRepo
  ) extends DeNoPaController(dictionaryRepo) {

  override protected val dataSetName = "DeNoPa First Visit"

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.firstvisit"

  override protected def router = StudyRouter.DeNoPaFirstVisit.dataSetRouter
}
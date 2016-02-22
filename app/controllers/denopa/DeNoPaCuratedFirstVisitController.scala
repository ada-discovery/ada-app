package controllers.denopa

import javax.inject.{Inject, Named}

import controllers.StudyRouter
import persistence.DictionaryFieldRepo

class DeNoPaCuratedFirstVisitController @Inject() (
    @Named("DeNoPaCuratedFirstVisitDictionaryRepo") repo: DictionaryFieldRepo
  ) extends DeNoPaController(repo) {

  override val dataSetName = "DeNoPa Curated First Visit"

  override protected val listViewColumns = Some(Seq("Line_Nr", "Probanden_Nr", "Geb_Datum", "b_Gruppe"))

  override protected val overviewFieldNamesConfPrefix = "denopa.curatedfirstvisit"

  override protected def router = StudyRouter.DeNoPaCuratedFirstVisit.dataSetRouter
}
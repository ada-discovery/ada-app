package controllers.core

import dataaccess.{AsyncReadonlyRepo, Criterion, JsonFormatRepoAdapter}
import dataaccess.Criterion._
import models.DataSetFormattersAndIds.FieldIdentity
import models.{Widget, WidgetGenerationMethod, WidgetSpec}
import persistence.dataset.CaseClassFieldRepo
import play.api.libs.json.Format
import services.WidgetGenerationService

import scala.concurrent.Future
import scala.reflect.runtime.universe._
import scala.concurrent.ExecutionContext.Implicits.global

trait WidgetRepoController[E] {

  protected def repo: AsyncReadonlyRepo[E, _]
  protected def typeTag: TypeTag[E]
  protected def format: Format[E]
  protected def excludedFieldNames: Traversable[String] = Nil
  protected def wgs: WidgetGenerationService

  protected lazy val fieldCaseClassRepo = CaseClassFieldRepo[E](excludedFieldNames, true)(typeTag)
  protected lazy val jsonCaseClassRepo = JsonFormatRepoAdapter.applyNoId(repo)(format)

  def widgets(
    widgetSpecs: Traversable[WidgetSpec],
    criteria: Seq[Criterion[Any]]
  ) : Future[Traversable[Option[Widget]]] =
    for {
      fields <- {
        val fieldNames = (criteria.map(_.fieldName) ++ widgetSpecs.flatMap(_.fieldNames)).toSet
        fieldCaseClassRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSeq))
      }

      widgets <- wgs(widgetSpecs, jsonCaseClassRepo, criteria, Map(), fields, WidgetGenerationMethod.FullData)
  } yield
      widgets
}

package services.request

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{Field, FieldTypeId, WidgetSpec}
import org.ada.web.services.DataSpaceService
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.dataaccess.{AscSort, Criterion, DescSort, Sort}
import org.incal.play.Page
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DataSetItemsProvider @Inject() (dsaf: DataSetAccessorFactory,   dataSpaceService: DataSpaceService) {


  def retrieveTableWithFilter(page: Int, dataSetId: String, orderBy: String, filterOrId: FilterOrId, fieldNames: Seq[String])(implicit request: AuthenticatedRequest[_]) = {

    val dsa: DataSetAccessor = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo
    val filterRepo = dsa.filterRepo
    val dataViewRepo = dsa.dataViewRepo
    val repo = dsa.dataSetRepo

    for {
      resolvedFilter <- filterRepo.resolve(filterOrId)
      criteria <- toCriteria(resolvedFilter.conditions)
      nameFieldMap <- createNameFieldMap(fieldRepo, Seq(resolvedFilter.conditions), Nil, fieldNames)
      tableItems <- getTableItems(repo, page, orderBy, criteria, nameFieldMap, fieldNames)
      count <- repo.count(criteria)
    } yield {
      val tablePage = Page(tableItems, page, page * 20, count, orderBy)
      val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten

      tablePage
  }
  }

    private def createNameFieldMap(fieldRepo: FieldRepo,
                                   conditions: Traversable[Traversable[FilterCondition]],
                                   widgetSpecs: Traversable[WidgetSpec],
                                   tableColumnNames: Traversable[String]
                                  )
    =
    {
      val filterFieldNames = conditions.flatMap(_.map(_.fieldName.trim))
      val widgetFieldNames = widgetSpecs.flatMap(_.fieldNames)

      getFields(fieldRepo, (tableColumnNames ++ filterFieldNames ++ widgetFieldNames).toSet).map { fields =>
        fields.map(field => (field.name, field)).toMap
      }
    }

  private def getFields(fieldRepo: FieldRepo,
                         fieldNames: Traversable[String]
                       ): Future[Traversable[Field]] =
    if (fieldNames.isEmpty)
      Future(Nil)
    else
      fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSet.toSeq))


protected def toCriteria(
filter: Seq[FilterCondition]
): Future[Seq[Criterion[Any]]] = {
val fieldNames = filter.seq.map(_.fieldName)

filterValueConverters(fieldNames).map(
FilterCondition.toCriteria(_, filter)
)
}

protected def filterValueConverters(
fieldNames: Traversable[String]
): Future[Map[String, String => Option[Any]]] =
Future(Map())


private def getTableItems(repo : JsonCrudRepo,
page: Int,
orderBy: String,
criteria: Seq[Criterion[Any]],
nameFieldMap: Map[String, Field],
tableFieldNames: Seq[String]
): Future[Traversable[JsObject]] = {
  val tableFieldNamesToLoad = tableFieldNames.filterNot { tableFieldName =>
  nameFieldMap.get(tableFieldName).map(field => field.isArray || field.fieldType == FieldTypeId.Json).getOrElse(false)
}
  if (tableFieldNamesToLoad.nonEmpty)
  getFutureItemsForCriteria(repo ,Some(page), orderBy, criteria, tableFieldNamesToLoad ++ Seq(JsObjectIdentity.name), Some(20))
  else
  Future(Nil)

}

  protected def getFutureItemsForCriteria(repo : JsonCrudRepo,
                                           page: Option[Int],
                                           orderBy: String,
                                           criteria: Seq[Criterion[Any]],
                                           projection: Seq[String],
                                           limit: Option[Int]
                                         ): Future[Traversable[JsObject]] = {
    val sort = toSort(orderBy)
    val skip = page.zip(limit).headOption.map { case (page, limit) =>
      page * limit
    }

    repo.find(criteria, sort, projection, limit, skip)
  }


  protected def toSort(fieldName : String): Seq[Sort] =
    if (fieldName.nonEmpty) {
      val sort = if (fieldName.startsWith("-"))
        DescSort(fieldName.substring(1, fieldName.length))
      else
        AscSort(fieldName)
      Seq(sort)
    } else
      Nil

}
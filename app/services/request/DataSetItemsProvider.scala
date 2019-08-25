package services.request

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{Field, FieldTypeId, Filter}
import org.ada.web.controllers.FilterConditionExtraFormats.coreFilterConditionFormat
import org.ada.web.controllers.dataset.TableViewData
import org.ada.web.services.DataSpaceService
import org.incal.core.ConditionType.{In, NotIn}
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.dataaccess.{AscSort, Criterion, DescSort, Sort}
import org.incal.play.{Page, PageOrder}
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// TODO: What the heck is this about?
class DataSetItemsProvider @Inject() (dsaf: DataSetAccessorFactory, dataSpaceService: DataSpaceService) {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  def retrieveTableWithFilterForSelection(
    page: Int,
    dataSetId: String,
    orderBy: String,
    filterOrIds: Seq[FilterOrId],
    filterOrId: Option[FilterOrId],
    fieldNames: Seq[String],
    itemIds: Option[Seq[Option[BSONObjectID]]],
    tablePages: Seq[PageOrder] = Nil)(
    implicit request: AuthenticatedRequest[_]
  ) = {

    val dsa: DataSetAccessor = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo
    val filterRepo = dsa.filterRepo
    val dataViewRepo = dsa.dataViewRepo
    val repo = dsa.dataSetRepo

    for {
      resolvedFilter <- filterRepo.resolve(filterOrId.get)
      criteria <- buildCriteria(resolvedFilter.conditions, itemIds)
      nameFieldMap <- createNameFieldMap(fieldRepo, Seq(resolvedFilter.conditions), fieldNames)
      tableItems <- getTableItems(repo, page, orderBy, criteria, nameFieldMap, fieldNames)
      count <- repo.count(criteria)
    } yield {
      val tablePage = Page(tableItems, page, page * 20, count, orderBy)
      val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten

      val newFilter = setFilterLabels(resolvedFilter, nameFieldMap)
      val conditionPanel = views.html.filter.conditionPanel(Some(newFilter))
      val filterModel = Json.toJson(resolvedFilter.conditions)

      (TableViewData(tablePage,Some(resolvedFilter), fieldsInOrder), newFilter)
    }
  }

def buildCriteria(conditions: Seq[FilterCondition], itemIds: Option[Seq[Option[BSONObjectID]]] )={
  val conditionsCriteria = toCriteria(conditions)

  itemIds match {
    case Some(idsOptions) => conditionsCriteria.map( _ ++ Seq("_id" #-> idsOptions))
    case None => conditionsCriteria
  }
}

    def setFilterLabels(
                                 filter: Filter,
                                 fieldNameMap: Map[String, Field]
                               ): Filter = {
      def valueStringToDisplayString[T](
                                         fieldType: FieldType[T],
                                         text: Option[String]
                                       ): Option[String] =
        text.map { text =>
          val value = fieldType.valueStringToValue(text.trim)
          fieldType.valueToDisplayString(value)
        }

      val newConditions = filter.conditions.map { condition =>
        fieldNameMap.get(condition.fieldName.trim) match {
          case Some(field) => {
            val fieldType = ftf(field.fieldTypeSpec)
            val value = condition.value

            val valueLabel = condition.conditionType match {
              case In | NotIn =>
                value.map(
                  _.split(",").flatMap(x => valueStringToDisplayString(fieldType, Some(x))).mkString(", ")
                )

              case _ => valueStringToDisplayString(fieldType, value)
            }
            condition.copy(fieldLabel = field.label, valueLabel = valueLabel)
          }
          case None => condition
        }
      }

      filter.copy(conditions = newConditions)
    }

      def createNameFieldMap(fieldRepo: FieldRepo,
                             conditions: Traversable[Traversable[FilterCondition]],
                             tableColumnNames: Traversable[String]
                            )= {
        val filterFieldNames = conditions.flatMap(_.map(_.fieldName.trim))


        getFields(fieldRepo, (tableColumnNames ++ filterFieldNames).toSet).map { fields =>
          fields.map(field => (field.name, field)).toMap
        }
      }

      def getFields(fieldRepo: FieldRepo,
                    fieldNames: Traversable[String]
                   ): Future[Traversable[Field]] =
        if (fieldNames.isEmpty)
          Future(Nil)
        else
          fieldRepo.find(Seq(FieldIdentity.name #-> fieldNames.toSet.toSeq))


      def toCriteria(
                      filter: Seq[FilterCondition]
                    ): Future[Seq[Criterion[Any]]] = {
        val fieldNames = filter.seq.map(_.fieldName)

        filterValueConverters(fieldNames).map(
          FilterCondition.toCriteria(_, filter)
        )
      }

      protected def filterValueConverters(
                                           fieldNames: Traversable[String]
                                         ): Future[Map[String, String => Option[Any]]]  =  {  Future(Map()) }


      def getTableItems(repo: JsonCrudRepo,
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
          getFutureItemsForCriteria(repo, Some(page), orderBy, criteria, tableFieldNamesToLoad ++ Seq(JsObjectIdentity.name), Some(20))
        else
          Future(Nil)
      }


  def getFutureItemsForCriteria(repo : JsonCrudRepo,
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

  def toSort(fieldName : String): Seq[Sort] =
    if (fieldName.nonEmpty) {
      val sort = if (fieldName.startsWith("-"))
        DescSort(fieldName.substring(1, fieldName.length))
      else
        AscSort(fieldName)
      Seq(sort)
    } else
      Nil

 def getItemsById(itemIds: Seq[BSONObjectID], dataSetId: String, fieldNames: Traversable[String])(implicit request: AuthenticatedRequest[_])={
 itemIds.size > 0 match {
   case true => for{
     items <- retrieveTableWithFilterForSelection(0, dataSetId, "",Seq(Right(itemIds(0)))  , Some(Left(List())), fieldNames.toSeq, Some(itemIds.map(Some(_))))
   }yield
     {
       Some(items)
     }
   case false => Future {
     None
   }
 }
  }
}
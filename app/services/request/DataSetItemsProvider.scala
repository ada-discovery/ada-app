package services.request

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import org.ada.server.dataaccess.RepoTypes.{FieldRepo, JsonCrudRepo}
import org.ada.server.dataaccess.dataset.FilterRepoExtra._
import org.ada.server.dataaccess.dataset.{DataSetAccessor, DataSetAccessorFactory}
import org.ada.server.field.{FieldType, FieldTypeHelper}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.Filter.FilterOrId
import org.ada.server.models.{Field, FieldTypeId, Filter, WidgetGenerationMethod, WidgetSpec}
import org.ada.web.controllers.dataset.TableViewData
import org.ada.web.services.DataSpaceService
import org.incal.core.ConditionType.{In, NotIn}
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.core.dataaccess.{AscSort, Criterion, DescSort, Sort}
import org.incal.play.{Page, PageOrder}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DataSetItemsProvider @Inject() (dsaf: DataSetAccessorFactory,   dataSpaceService: DataSpaceService) {

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  def retrieveTableWithFilterForSelection(page: Int, dataSetId: String, orderBy: String, filterOrIds: Seq[FilterOrId],filterOrId: Option[FilterOrId], fieldNames: Seq[String], itemIds: Option[Seq[Option[BSONObjectID]]], tablePages: Seq[PageOrder] = Nil)(implicit request: AuthenticatedRequest[_]) = {

    val dsa: DataSetAccessor = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo
    val filterRepo = dsa.filterRepo
    val dataViewRepo = dsa.dataViewRepo
    val repo = dsa.dataSetRepo

    for {
         resolvedFilter <- filterRepo.resolve(filterOrId.get)
        criteria <- toCriteria(resolvedFilter.conditions)
       nameFieldMap <- createNameFieldMap(fieldRepo, Seq(resolvedFilter.conditions), fieldNames)
      resolvedFilters <- Future.sequence(filterOrIds.map(filterRepo.resolve))
         criteria <- toCriteria(resolvedFilter.conditions)
     nameFieldMap <- createNameFieldMap(fieldRepo, Seq(resolvedFilter.conditions), fieldNames)
     tableItems <- getTableItems(repo, page, orderBy, criteria, nameFieldMap, fieldNames)
         count <- repo.count(criteria)
    } yield {
         val tablePage = Page(tableItems, page, page * 20, count, orderBy)
        val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten
      tablePage
    }
  }


  def retrieveTableWithFilterByItemIds(page: Int, dataSetId: String, orderBy: String, filterOrIds: Seq[FilterOrId],filterOrId: Option[FilterOrId], fieldNames: Seq[String], itemIds: Option[Seq[Option[BSONObjectID]]], tablePages: Seq[PageOrder] = Nil)(implicit request: AuthenticatedRequest[_]) = {

    val dsa: DataSetAccessor = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo
    val filterRepo = dsa.filterRepo
    val dataViewRepo = dsa.dataViewRepo
    val repo = dsa.dataSetRepo

    for {
   //   resolvedFilter <- filterRepo.resolve(filterOrId)
    //  criteria <- toCriteria(resolvedFilter.conditions)
     // nameFieldMap <- createNameFieldMap(fieldRepo, Seq(resolvedFilter.conditions), fieldNames)



      resolvedFilters <- Future.sequence(filterOrIds.map(filterRepo.resolve))
    //  criteria <- toCriteria(conditions)
    //  nameFieldMap <- createNameFieldMap(fieldRepo, conditions, Nil, fieldNames)
  //    tableItems <- getTableItems(repo, page, orderBy, criteria, nameFieldMap, fieldNames)
  //    count <- repo.count(criteria)

      filterOrIdsToUse = filterOrIds


      // initialize table pages
      tablePagesToUse = {
        val padding = Seq.fill(Math.max(filterOrIdsToUse.size - tablePages.size, 0))(PageOrder(0, ""))
        tablePages ++ padding
      }

      // table column names and widget specs
      //(tableColumnNames, widgetSpecs) = fieldNames.map(f => (f, Nil))


      // load all the filters if needed
    //resolvedFilters <- Future.sequence(filterOrIds.map(filterRepo.resolve))

      // collect the filters' conditions
      conditions = resolvedFilters.map(_.conditions)

      // convert the conditions to criteria
      criteria <- Future.sequence(conditions.map(toCriteria))

      criteriaWithIds = //criteria

      if (itemIds.isDefined) {
        criteria.map( _ ++ Seq("_id" #-> itemIds.get))
            }
      else
        {
          criteria
        }



      //criteria
     // criteria.map( _ ++ Seq("_id" #-> Seq(Some(BSONObjectID.parse("5cc6c0d9110100200306cd15").get))))

      // create a name -> field map of all the referenced fields for a quick lookup
      nameFieldMap <- createNameFieldMap(fieldRepo, conditions, fieldNames)


      viewResponses <-
      Future.sequence(
        (tablePagesToUse, criteriaWithIds, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
          getInitViewResponse(repo, tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, fieldNames)
        }
      )




      viewResponse <-  getInitViewResponse(repo, 0, "", resolvedFilters(0), criteriaWithIds(0), nameFieldMap, fieldNames)



      /*
      viewResponses <-

      Future.sequence(
        (tablePagesToUse, criteria, resolvedFilters).zipped.map { case (tablePage, criteria, resolvedFilter) =>
          getInitViewResponse(repo, tablePage.page, tablePage.orderBy, resolvedFilter, criteria, nameFieldMap, fieldNames)
        }
      )
      */

    } yield {
   //   val tablePage = Page(tableItems, page, page * 20, count, orderBy)
    //  val fieldsInOrder = fieldNames.map(nameFieldMap.get).flatten


      val viewFutures = (viewResponses, tablePagesToUse, criteriaWithIds).zipped.map {
        case (viewResponse, tablePage, criteria2) =>
          val newPage = Page(viewResponse.tableItems, tablePage.page, tablePage.page * 20, viewResponse.count, tablePage.orderBy)
          val viewData = TableViewData(newPage, Some(viewResponse.filter), viewResponse.tableFields)
          viewData
      }

       viewFutures
     // tablePage
  }
  }


  case class InitViewResponse(
                                       count: Int,
                                       tableItems: Traversable[JsObject],
                                       filter: Filter,
                                       tableFields: Traversable[Field]
                                     )

  def getInitViewResponse(
                                   repo : JsonCrudRepo,
                                   page: Int,
                                   orderBy: String,
                                   filter: Filter,
                                   criteria: Seq[Criterion[Any]],
                                   nameFieldMap: Map[String, Field],
                                   tableFieldNames: Seq[String]
                                 ): Future[InitViewResponse] = {

    // total count
    val countFuture = repo.count(criteria)

    // table items
    val tableItemsFuture = getTableItems(repo, page, orderBy, criteria, nameFieldMap, tableFieldNames)

    for {
      // obtain the total item count satisfying the resolved filter
      count <- countFuture

      // load the table items
      tableItems <- tableItemsFuture
    } yield {
      val tableFields = tableFieldNames.map(nameFieldMap.get).flatten
      val newFilter = setFilterLabels(filter, nameFieldMap)
      InitViewResponse(count, tableItems, newFilter, tableFields)
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
                            )
      = {
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



  def getItemsById(itemIds: Seq[BSONObjectID], dataSetId: String, fieldNames: Seq[String])(implicit request: AuthenticatedRequest[_])={

 itemIds.size > 0 match {
   case true => for{
     items <- retrieveTableWithFilterByItemIds(0, dataSetId, "",Seq(Right(itemIds(0)))  , None, fieldNames.toSeq, Some(itemIds.map(Some(_))))
   }yield
     {
       Some(items(0))
     }
   case false => Future {None}
 }


  }
}
package controllers

import java.util.concurrent.TimeoutException
import javax.inject.Inject

import models.{MetaTypeStats, Page}
import persistence.DeNoPaBaselineMetaTypeStatsRepo
import play.api.Logger
import play.api.data.Forms.{date, ignored, mapping, nonEmptyText, bigDecimal}
import play.api.i18n.MessagesApi
import play.api.mvc.{Flash, Action, Controller, RequestHeader}
import play.api.libs.json.{JsObject, Json}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID

class MetaTypeStatsController @Inject() (
    baselineMetaTypeStatsRepo: DeNoPaBaselineMetaTypeStatsRepo,
    MmetaTypeStatsRepo: DeNoPaBaselineMetaTypeStatsRepo,
    messagesApi: MessagesApi
  ) extends Controller {

//  override val form = Form(
//    mapping(
//      "id" -> ignored(BSONObjectID.generate: BSONObjectID),
//      "attributeName" -> nonEmptyText,
//      "intRatio" -> bigDecimal,
//      "longRatio" -> bigDecimal,
//      "floatRatio" -> bigDecimal,
//      "doubleRatio" -> bigDecimal,
//      "booleanRatio" -> bigDecimal,
//      "nullRatio" -> bigDecimal,
//      "valueRationMap" -> nonEmptyText)(MetaTypeStats.apply)(MetaTypeStats.unapply))

  def get(id: BSONObjectID) = Action.async { implicit request =>
    baselineMetaTypeStatsRepo.get(id).map(_.fold(
      NotFound(s"Entity #$id not found")
    ){ entity =>
      implicit val msg = messagesApi.preferred(request)

      Ok(views.html.denopametatype.show(entity))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the get process")
        InternalServerError(t.getMessage)
    }
  }

  /**
   * Display the paginated list.
   *
   * @param page Current page number (starts from 0)
   * @param orderBy Column to be sorted
   * @param query Filter applied on items
   */
  def find(page: Int, orderBy: String, query: String, searchField : String) = Action.async { implicit request =>
    val limit = 20
    val criteria = if (!query.isEmpty)
      if (searchField == "attributeName")
        Some(Json.obj(searchField -> Json.obj("$regex" -> (query + ".*"), "$options" -> "i")))
      else
        try {
          Some(Json.obj(searchField -> query.toDouble))
        } catch {
          case e : NumberFormatException => None
        }
    else
      None
    val sort = Json.obj(orderBy -> 1)

    val futureItems = baselineMetaTypeStatsRepo.find(criteria, Some(sort), None, Some(limit), Some(page))
    val futureCount = baselineMetaTypeStatsRepo.count(criteria)
    futureItems.zip(futureCount).map({ case (items, count) =>
      implicit val msg = messagesApi.preferred(request)

      Ok(views.html.denopametatype.list(Page(items, page, page * limit, count), orderBy, if (criteria.isDefined) query else "", searchField))
    }).recover {
      case t: TimeoutException =>
        Logger.error("Problem found in the list process")
        InternalServerError(t.getMessage)
    }
  }
}
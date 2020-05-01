package org.ada.server.models

import java.util.Date

import org.ada.server.dataaccess.BSONObjectIdentity
import org.ada.server.json.GenericJson
import org.incal.core.runnables.InputRunnable
import org.incal.core.util.ReflectionUtil
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json}
import reactivemongo.bson.BSONObjectID
import RunnableSpec.{format => runnableSpecFormat}
import scala.reflect.runtime.universe._

case class InputRunnableSpec[+IN](
  _id: Option[BSONObjectID],

  input: IN,
  runnableClassName: String,
  name: String,

  scheduled: Boolean = false,
  scheduledTime: Option[ScheduledTime] = None,
  timeCreated: Date = new Date(),
  timeLastExecuted: Option[Date] = None
) extends BaseRunnableSpec

object InputRunnableSpec {

  implicit val genericFormat: Format[InputRunnableSpec[Any]] = new InputRunnableSpecFormat

  implicit object InputRunnableSpecIdentity extends BSONObjectIdentity[InputRunnableSpec[Any]] {
    def of(entity: InputRunnableSpec[Any]): Option[BSONObjectID] = entity._id
    protected def set(entity: InputRunnableSpec[Any], id: Option[BSONObjectID]) = entity.copy(_id = id)
  }
}

private final class InputRunnableSpecFormat extends Format[InputRunnableSpec[Any]] {

  private val currentMirror = ReflectionUtil.newCurrentThreadMirror

  override def writes(o: InputRunnableSpec[Any]): JsValue = {
    val runnableSpec = RunnableSpec(
      _id = o._id,
      runnableClassName = o.runnableClassName,
      name = o.name,
      scheduled = o.scheduled,
      scheduledTime = o.scheduledTime,
      timeCreated = o.timeCreated,
      timeLastExecuted = o.timeLastExecuted
    )

    val jsObject = Json.toJson(runnableSpec)(runnableSpecFormat).asInstanceOf[JsObject]

    genericInputFormat(runnableSpec).map { inputFormat =>
      val inputJson = Json.toJson(o.input)(inputFormat)
      jsObject.+("input" -> inputJson)
    }.getOrElse(
      jsObject // something went wrong... it's not an input runnable
    )
  }

  override def reads(json: JsValue): JsResult[InputRunnableSpec[Any]] = {
    json.validate[RunnableSpec](runnableSpecFormat) match {
      case JsSuccess(o, path) =>
        genericInputFormat(o).map { inputFormat =>
          val input = (json \ "input").as[Any](inputFormat)

          val spec = InputRunnableSpec(
            _id = o._id,
            runnableClassName = o.runnableClassName,
            name = o.name,
            scheduled = o.scheduled,
            scheduledTime = o.scheduledTime,
            timeCreated = o.timeCreated,
            timeLastExecuted = o.timeLastExecuted,
            input = input
          )

          JsSuccess(spec, path)
        }.getOrElse(
          JsError(s"JSON '${Json.prettyPrint(json)}' is not an input runnable spec.")
        )
      case x: JsError => x
    }
  }

  private def genericInputFormat(
    runnableSpec: RunnableSpec
  ): Option[Format[Any]] = {
    val runnableType = ReflectionUtil.classNameToRuntimeType(runnableSpec.runnableClassName, currentMirror)

    if (runnableType <:< typeOf[InputRunnable[_]]) {
      val inputBaseType = runnableType.baseType(typeOf[InputRunnable[_]].typeSymbol)
      val inputType = inputBaseType.typeArgs.head
      Some(GenericJson.format(inputType))
    } else
      None
  }
}
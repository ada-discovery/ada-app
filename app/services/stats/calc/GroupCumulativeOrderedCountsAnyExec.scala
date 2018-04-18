package services.stats.calc

import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Source}
import models.{Field, FieldTypeId}
import play.api.libs.json.JsObject
import services.stats.CalculatorExecutor
import services.stats.CalculatorHelper._
import util.FieldUtil.{fieldTypeOrdering, valueOrdering}
import scala.reflect.runtime.universe._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

private class GroupCumulativeOrderedCountsAnyExec[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, Any]#IN]
  ) extends CalculatorExecutor[GroupCumulativeOrderedCountsCalcTypePack[G, Any], (Field, Field)] {

  private def anyExec(
    implicit ordering: Ordering[Any]
  ) = {
    CalculatorExecutor.with2Tuple(GroupCumulativeOrderedCountsCalc.apply[G, Any])
  }

  override def exec(
    options: Unit)(
    values: Traversable[(Option[G], Option[Any])]
  ) =
    values.find(_._2.isDefined).map { case (_, Some(someVal)) =>
      dispatchVal(
        _.exec(options)(values)
      )(someVal, Nil)
    }.getOrElse(
      Nil
    )

  override def execJson(
    options: Unit,
    fields: (Field, Field))(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJson(options, fields)(jsons)
    )(fields, Nil)

  override def execJsonA(
    options: Unit,
    scalarOrArrayField: Field,
    fields: (Field, Field))(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJsonA(options, scalarOrArrayField, fields)(jsons)
    )(fields, Nil)

  override def execJsonStreamed(
    flowOptions: Unit,
    postFlowOptions: Unit,
    fields: (Field, Field))(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamed(flowOptions, postFlowOptions, fields)(source)
    )(fields, Future(Nil))

  override def execJsonStreamedA(
    flowOptions: Unit,
    postFlowOptions: Unit,
    scalarOrArrayField: Field,
    fields: (Field, Field))(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamedA(flowOptions, postFlowOptions, scalarOrArrayField, fields)(source)
    )(fields, Future(Nil))

  override def createJsonFlow(
    options: Unit,
    fields: (Field, Field)
  ) = dispatch(
      _.createJsonFlow(options, fields)
    )(fields, Flow[JsObject].map(_ => Nil))

  override def createJsonFlowA(
    options: Unit,
    scalarOrArrayField: Field,
    fields: (Field, Field)
  ) = dispatch(
      _.createJsonFlowA(options, scalarOrArrayField, fields)
    )(fields, Flow[JsObject].map(_ => Nil))

  override def execStreamed(
    flowOptions: Unit,
    postFlowOptions: Unit)(
    source: Source[(Option[G], Option[Any]), _])(
    implicit materializer: Materializer
  ) =
    throw new RuntimeException("Method GroupCumulativeOrderedCountsAnyExec.execStreamed is not supported due to unknown value type (ordering).")

  private def dispatch[OUT](
    exec: CalculatorExecutor[GroupCumulativeOrderedCountsCalcTypePack[G, Any], (Field, Field)] => OUT)(
    fields: (Field, Field),
    defaultOutput: OUT
  ): OUT =
    fieldTypeOrdering(fields._2.fieldType).map { implicit ordering =>
      exec(anyExec)
    }.getOrElse(defaultOutput)

  private def dispatchVal[OUT](
    exec: CalculatorExecutor[GroupCumulativeOrderedCountsCalcTypePack[G, Any], (Field, Field)] => OUT)(
    value: Any,
    defaultOutput: OUT
  ): OUT =
    valueOrdering(value).map { implicit ordering =>
      exec(anyExec)
    }.getOrElse(defaultOutput)
}

object GroupCumulativeOrderedCountsAnyExec {
  def apply[G](
    implicit inputTypeTag: TypeTag[GroupCumulativeOrderedCountsCalcTypePack[G, Any]#IN]
  ): CalculatorExecutor[GroupCumulativeOrderedCountsCalcTypePack[G, Any], (Field, Field)] = new GroupCumulativeOrderedCountsAnyExec[G]
}
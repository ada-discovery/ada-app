package services.stats.calc

import akka.NotUsed
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

private object CumulativeOrderedCountsAnyExecAux extends CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Field] {

  private def genericExec[T: Ordering](
    implicit inputTypeTag: TypeTag[T]
  ) = CalculatorExecutor.withSingle(CumulativeOrderedCountsCalc[T])

  override def exec(
    options: Unit)(
    values: Traversable[Option[Any]]
  ) =
    values.find(_.isDefined).map ( someVal =>
      dispatchVal(
        _.exec(options)(values)
      )(someVal, Nil)
    ).getOrElse(
      Nil
    )

  override def execJson(
    options: Unit,
    field: Field)(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJson(options, field)(jsons)
    )(field, Nil)

  override def execJsonA(
    options: Unit,
    scalarOrArrayField: Field,
    field: Field)(
    jsons: Traversable[JsObject]
  ) = dispatch(
      _.execJsonA(options, scalarOrArrayField, field)(jsons)
    )(field, Nil)

  override def execJsonStreamed(
    flowOptions: Unit,
    postFlowOptions: Unit,
    field: Field)(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamed(flowOptions, postFlowOptions, field)(source)
    )(field, Future(Nil))

  override def execJsonStreamedA(
    flowOptions: Unit,
    postFlowOptions: Unit,
    scalarOrArrayField: Field,
    field: Field)(
    source: Source[JsObject, _])(
    implicit materializer: Materializer
  ) = dispatch(
      _.execJsonStreamedA(flowOptions, postFlowOptions, scalarOrArrayField, field)(source)
    )(field, Future(Nil))

  override def createJsonFlow(
    options: Unit,
    field: Field
  ) = dispatch(
      _.createJsonFlow(options, field)
    )(field, Flow[JsObject].map(_ => Nil))

  override def createJsonFlowA(
    options: Unit,
    scalarOrArrayField: Field,
    field: Field
  ) = dispatch(
      _.createJsonFlowA(options, scalarOrArrayField, field)
    )(field, Flow[JsObject].map(_ => Nil))

  override def execStreamed(
    flowOptions: Unit,
    postFlowOptions: Unit)(
    source: Source[Option[Any], _])(
    implicit materializer: Materializer
  ) =
    throw new RuntimeException("Method CumulativeOrderedCountsAnyExec.execStreamed is not supported due to unknown value type (ordering).")

  private def dispatch[OUT](
    exec: CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Field] => OUT)(
    field: Field,
    defaultOutput: OUT
  ): OUT =
    fieldTypeOrdering(field.fieldType).map { implicit ordering =>
      exec(genericExec[Any])
    }.getOrElse(defaultOutput)

  private def dispatchVal[OUT](
    exec: CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Field] => OUT)(
    value: Any,
    defaultOutput: OUT
  ): OUT =
    valueOrdering(value).map { implicit ordering =>
      exec(genericExec[Any])
    }.getOrElse(defaultOutput)
}

object CumulativeOrderedCountsAnyExec {
  def apply: CalculatorExecutor[CumulativeOrderedCountsCalcTypePack[Any], Field] = CumulativeOrderedCountsAnyExecAux
}
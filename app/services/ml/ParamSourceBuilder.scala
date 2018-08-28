package services.ml

import models.ml.classification.ValueOrSeq.ValueOrSeq
import org.apache.spark.ml.param.{Param, ParamMap, Params}
import org.apache.spark.ml.tuning.ParamGridBuilder

import scala.collection.mutable.Buffer

case class ParamSourceBinder[S, T <: Params](source: S, model: T) {
  private var paramValueSetters: Buffer[ParamValueSetter[S, _]] = Buffer[ParamValueSetter[S, _]]()

  def bind[T](value: S => Option[T], paramName: String): this.type =
    bindAux({x => Left(value(x))}, model.getParam(paramName))

  def bind2[T](value: S => ValueOrSeq[T], paramName: String): this.type =
    bindAux(value, model.getParam(paramName))

  private def bindAux[T](values: S => Either[Option[T], Iterable[T]], param: Param[T]): this.type = {
    paramValueSetters.append(ParamValueSetter(param, values))
    this
  }

  def build: (T, Array[ParamMap]) = {
    val paramGrid = new ParamGridBuilder()
    paramValueSetters.foreach(_.set(model, paramGrid, source))
    (model, paramGrid.build())
  }
}

case class ParamValueSetter[S, T](
  val param: Param[T],
  value: S => (Either[Option[T], Iterable[T]])
) {
  def set(
    params: Params,
    paramGridBuilder: ParamGridBuilder,
    source: S
  ): Unit = value(source) match {
    case Left(valueOption) => valueOption.foreach(params.set(param, _))
    case Right(values) => paramGridBuilder.addGrid(param, values)
  }
}

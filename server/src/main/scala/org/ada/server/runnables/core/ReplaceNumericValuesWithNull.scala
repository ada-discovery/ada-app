package org.ada.server.runnables.core

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Sink
import org.ada.server.models.FieldTypeId
import play.api.Logger
import play.api.libs.json.{JsObject, _}
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps, NamedFieldType}
import akka.actor.ActorSystem
import org.ada.server.dataaccess.dataset.DataSetAccessor

import scala.concurrent.ExecutionContext.Implicits.global

class ReplaceNumericValuesWithNull extends DsaInputFutureRunnable[ReplaceNumericValuesWithNullSpec] {

  private val logger = Logger

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(spec: ReplaceNumericValuesWithNullSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // fields
      numericFields <- dsa.fieldRepo.find(
        criteria = Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer))
      )

      nameFieldTypes = numericFields.map(_.toNamedTypeAny).toSeq

      // get a stream
      stream <- dsa.dataSetRepo.findAsStream()

      // group and update the items from the stream as it goes
      _ <- {
        logger.info(s"Streaming and updating data from ${spec.dataSetId}...")
        stream
          .grouped(spec.processingBatchSize)
          .buffer(spec.backpressureBufferSize, OverflowStrategy.backpressure)
          .mapAsync(spec.parallelism)(updateJsons(dsa, nameFieldTypes, spec.valueToReplace))
          .runWith(Sink.ignore)
      }
    } yield
      ()

  // aux function to replace values with null and update jsons
  private def updateJsons(
    dsa: DataSetAccessor,
    fieldTypes: Seq[NamedFieldType[Any]],
    valueToReplace: Double)(
    jsons: Traversable[JsObject]
  ) = {
    logger.info(s"Processing ${jsons.size} items...")

    val jsonsToUpdate = jsons.map { json =>
      val fieldValuesToReplace = fieldTypes.zip(json.toValues(fieldTypes)).flatMap {
        case ((fieldName, _), value) =>
          if ((value.isDefined) && (value.get == valueToReplace)) Some((fieldName, JsNull)) else None
      }

      json ++ JsObject(fieldValuesToReplace)
    }

    dsa.dataSetRepo.update(jsonsToUpdate)
  }
}

case class ReplaceNumericValuesWithNullSpec(
  dataSetId: String,
  valueToReplace: Double,
  processingBatchSize: Int,
  parallelism: Int,
  backpressureBufferSize: Int
)
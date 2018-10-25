package runnables.core

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.Sink
import field.{FieldType, FieldTypeHelper}
import models.DataSetFormattersAndIds.JsObjectIdentity
import models.{AdaException, FieldTypeId}
import play.api.Logger
import play.api.libs.json.{JsObject, _}
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion.Infix
import _root_.util.FieldUtil.JsonFieldOps
import akka.actor.ActorSystem

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class CovertNumericValuesToNull extends DsaInputFutureRunnable[CovertNumericValuesToNullSpec] {

  private val logger = Logger // (this.getClass())

  private val ftf = FieldTypeHelper.fieldTypeFactory()
  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  override def runAsFuture(spec: CovertNumericValuesToNullSpec) = {
    val dsa_ = dsa(spec.dataSetId)
    val dataSetRepo = dsa_.dataSetRepo

    // aux function to replace values with null and update jsons
    def updateJsons(
      fieldNameTypes: Traversable[(String, FieldType[_])])(
      jsons: Traversable[JsObject]
    ) = {
      logger.info(s"Processing ${jsons.size} items...")
      val jsonsToUpdate = jsons.map { json =>
        val fieldsToReplace = fieldNameTypes.flatMap { case (fieldName, fieldType) =>
          val value = json.toValue(fieldName, fieldType)

          if ((value.isDefined) && (value.get == spec.valueToReplace)) Some((fieldName, JsNull)) else None
        }

        json ++ JsObject(fieldsToReplace.toSeq)
      }

      dataSetRepo.update(jsonsToUpdate)
    }

    for {
      // fields
      numericFields <- dsa_.fieldRepo.find(
        criteria = Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer))
      )

      fieldNameTypes = numericFields.map(field => (field.name, ftf(field.fieldTypeSpec)))

      // get a stream
      stream <- dataSetRepo.findAsStream()

      // group and updates the items from the stream as it goes
      _ <- {
        logger.info(s"Streaming and updating data from ${spec.dataSetId}...")
        stream
          .grouped(spec.processingBatchSize)
          .buffer(spec.backpressureBufferSize, OverflowStrategy.backpressure)
          .mapAsync(spec.parallelism)(updateJsons(fieldNameTypes))
          .runWith(Sink.ignore)
      }
    } yield
      ()
  }

  override def inputType = typeOf[CovertNumericValuesToNullSpec]
}

case class CovertNumericValuesToNullSpec(
  dataSetId: String,
  valueToReplace: Double,
  processingBatchSize: Int,
  parallelism: Int,
  backpressureBufferSize: Int
)
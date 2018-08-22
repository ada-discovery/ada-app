package runnables.core

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import field.FieldTypeHelper

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class FindDuplicates extends DsaInputFutureRunnable[FindDuplicatesSpec] {

  private val logger = Logger // (this.getClass())

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(input: FindDuplicatesSpec) = {
    val dsa_ = dsa(input.dataSetId)
    val repo = dsa_.dataSetRepo
    val fieldRepo = dsa_.fieldRepo

    val jsonsFuture = repo.find(projection = input.fieldNames)
    val fieldsFuture  = fieldRepo.find(Seq(FieldIdentity.name #-> input.fieldNames))

    for {
      // get the items
      jsons <- jsonsFuture

      // get the fields
      fields <- fieldsFuture
    } yield {
      val fieldNameTypes = fields.map( field => (field.name, ftf(field.fieldTypeSpec))).toSeq

      val values = jsons.map { json =>
        fieldNameTypes.map { case (name, fieldType) =>
          fieldType.jsonToValue(json \ name)
        }
      }

      val  duplicates = values.groupBy(identity).collect { case (x, Seq(_,_,_*)) => x }
      logger.info("Duplicates found: " + duplicates.size)
      logger.info("-----------------")
      logger.info(duplicates.mkString(", ") + "\n")
    }
  }

  override def inputType = typeOf[FindDuplicatesSpec]
}

case class FindDuplicatesSpec(dataSetId: String, fieldNames: Seq[String])
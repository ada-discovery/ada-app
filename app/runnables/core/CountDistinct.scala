package runnables.core

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import dataaccess.Criterion._
import field.FieldTypeHelper

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class CountDistinct extends DsaInputFutureRunnable[CountDistinctSpec] {

  private val logger = Logger // (this.getClass())

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(input: CountDistinctSpec) = {
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

      val distinctValues = values.groupBy(identity)
      logger.info("Distinct values found: " + distinctValues.size)
      logger.info("-----------------")
      distinctValues.foreach { case (value, items) =>
        logger.info(value.mkString(", ") + " : " + items.size)
      }
    }
  }

  override def inputType = typeOf[CountDistinctSpec]
}

case class CountDistinctSpec(dataSetId: String, fieldNames: Seq[String])
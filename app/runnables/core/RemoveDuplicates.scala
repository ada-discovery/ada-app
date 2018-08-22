package runnables.core

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import field.FieldTypeHelper
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class RemoveDuplicates extends DsaInputFutureRunnable[RemoveDuplicatesSpec] {

  private val logger = Logger // (this.getClass())

  private val idName = JsObjectIdentity.name
  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(input: RemoveDuplicatesSpec) = {
    val dsa_ = dsa(input.dataSetId)
    val repo = dsa_.dataSetRepo
    val fieldRepo = dsa_.fieldRepo

    val jsonsFuture = repo.find(projection = input.fieldNames ++ Seq(idName))
    val fieldsFuture  = fieldRepo.find(Seq(FieldIdentity.name #-> input.fieldNames))

    for {
      // get the items
      jsons <- jsonsFuture

      // get the fields
      fields <- fieldsFuture

      // remove the duplicates
      _ <- {
        val fieldNameTypes = fields.map( field => (field.name, ftf(field.fieldTypeSpec))).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = fieldNameTypes.map { case (name, fieldType) =>
            fieldType.jsonToValue(json \ name)
          }
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        val idsToRemove = valuesWithIds.groupBy(_._1).filter(_._2.size > 1).flatMap { case (_, items) => items.map(_._2).tail }

        logger.info(s"Removing ${idsToRemove.size } duplicates")
        repo.delete(idsToRemove)
      }
    } yield
      ()
  }

  override def inputType = typeOf[RemoveDuplicatesSpec]
}

case class RemoveDuplicatesSpec(dataSetId: String, fieldNames: Seq[String])
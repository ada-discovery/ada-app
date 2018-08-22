package runnables.core

import models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import field.FieldTypeHelper
import play.api.libs.json.Json
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class VerifyIfDuplicatesAreSame extends DsaInputFutureRunnable[VerifyIfDuplicatesAreSameSpec] {

  private val logger = Logger // (this.getClass())

  private val idName = JsObjectIdentity.name

  private val ftf = FieldTypeHelper.fieldTypeFactory()

  override def runAsFuture(input: VerifyIfDuplicatesAreSameSpec) = {
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

      // find unmatched duplicates
      unMatchedDuplicates <- {
        val fieldNameTypes = fields.map( field => (field.name, ftf(field.fieldTypeSpec))).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = fieldNameTypes.map { case (name, fieldType) =>
            fieldType.jsonToValue(json \ name)
          }
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        util.seqFutures(valuesWithIds.groupBy(_._1).filter(_._2.size > 1)) { case (values, items) =>
          val ids = items.map(_._2)
          repo.find(Seq(idName #-> ids.toSeq)).map { jsons =>
            // TODO: ugly... introduce a nested json comparator
            val head = Json.stringify(jsons.head.-(idName))
            val matched = jsons.tail.forall(json => Json.stringify(json.-(idName)).equals(head))
            if (!matched) {
              Some((values, ids))
            } else
              None
          }
        }
      }
    } yield {
      val duplicates = unMatchedDuplicates.flatten
      logger.info("Unmatched Duplicates found: " + duplicates.size)
      logger.info("------------------------------")
      logger.info(duplicates.map(x => x._1.mkString(",")).mkString("\n"))
    }
  }

  override def inputType = typeOf[VerifyIfDuplicatesAreSameSpec]
}

case class VerifyIfDuplicatesAreSameSpec(dataSetId: String, fieldNames: Seq[String])
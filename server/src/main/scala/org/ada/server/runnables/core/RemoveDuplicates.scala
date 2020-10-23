package org.ada.server.runnables.core

import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class RemoveDuplicates extends DsaInputFutureRunnable[RemoveDuplicatesSpec] {

  private val logger = Logger

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: RemoveDuplicatesSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetRepo.find(projection = input.fieldNames ++ Seq(idName))

      // get the fields
      fields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> input.fieldNames))

      // remove the duplicates
      _ <- {
        val namedFieldTypes = fields.map(_.toNamedTypeAny).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = json.toValues(namedFieldTypes)
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        val idsToRemove = valuesWithIds.groupBy(_._1).filter(_._2.size > 1).flatMap { case (_, items) => items.map(_._2).tail }

        logger.info(s"Removing ${idsToRemove.size } duplicates")
        dsa.dataSetRepo.delete(idsToRemove)
      }
    } yield
      ()
}

case class RemoveDuplicatesSpec(dataSetId: String, fieldNames: Seq[String])
package org.ada.server.runnables.core

import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import org.incal.core.util.seqFutures
import play.api.libs.json.Json
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps}

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class VerifyIfDuplicatesAreSame extends DsaInputFutureRunnable[VerifyIfDuplicatesAreSameSpec] {

  private val logger = Logger

  private val idName = JsObjectIdentity.name

  override def runAsFuture(input: VerifyIfDuplicatesAreSameSpec) =
    for {
      dsa <- createDsa(input.dataSetId)

      // get the items
      jsons <- dsa.dataSetRepo.find(projection = input.keyFieldNames ++ Seq(idName))

      // get the key fields
      keyFields <- dsa.fieldRepo.find(Seq(FieldIdentity.name #-> input.keyFieldNames))

      // get all the fields
      allFields <- dsa.fieldRepo.find()

      // compare field names
      compareFieldNames = if (input.compareFieldNamesToExclude.nonEmpty) allFields.map(_.name).filterNot(input.compareFieldNamesToExclude.contains(_)) else Nil

      // find unmatched duplicates
      unMatchedDuplicates <- {
        val namedFieldTypes = keyFields.map(_.toNamedTypeAny).toSeq

        val valuesWithIds = jsons.map { json =>
          val values = json.toValues(namedFieldTypes)
          val id = (json \ idName).as[BSONObjectID]
          (values, id)
        }

        seqFutures(valuesWithIds.groupBy(_._1).filter(_._2.size > 1)) { case (values, items) =>
          val ids = items.map(_._2)

          dsa.dataSetRepo.find(
            criteria = Seq(idName #-> ids.toSeq),
            projection = compareFieldNames
          ).map { jsons =>
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

case class VerifyIfDuplicatesAreSameSpec(
  dataSetId: String,
  keyFieldNames: Seq[String],
  compareFieldNamesToExclude: Seq[String]
)
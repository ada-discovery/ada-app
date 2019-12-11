package org.ada.server.services.transformers

import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import org.ada.server.AdaException
import org.ada.server.dataaccess.dataset.DataSetAccessor
import org.ada.server.field.FieldType
import org.ada.server.field.FieldUtil.{FieldOps, JsonFieldOps, NamedFieldType, fieldTypeOrdering}
import org.ada.server.models.DataSetFormattersAndIds.{FieldIdentity, JsObjectIdentity}
import org.ada.server.models.Field
import org.ada.server.models.datatrans.{LinkMultiDataSetsTransformation, LinkedDataSetSpec}
import org.incal.core.dataaccess.{AscSort, NotEqualsNullCriterion}
import org.incal.core.util.crossProduct
import org.incal.core.dataaccess.Criterion._
import play.api.libs.json.{JsObject, Json}
import org.incal.core.util.GroupMapList
import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class LinkMultiDataSetsOptimalTransformer extends AbstractDataSetTransformer[SingleFieldLinkMultiDataSetsTransformation] {

  override protected def execInternal(
    spec: SingleFieldLinkMultiDataSetsTransformation
  ) = {

    // aux function that creates a data stream for a given dsa
    def createStream(info: LinkedDataSetInfo) = {
      if(info.fieldNames.nonEmpty)
        logger.info(s"Creating a stream for these fields ${info.fieldNames.mkString(",")}.")
      else
        logger.info(s"Creating a stream for all available fields.")

      info.dsa.dataSetRepo.findAsStream(
        sort = Seq(AscSort(info.linkFieldName)),
        projection = info.fieldNames
      )
    }

    // aux function to create ordering for the link field
    def linkOrdering(
      info: LinkedDataSetInfo
    ): Ordering[Any] = {
      val fieldType = info.linkFieldType._2
      fieldTypeOrdering(fieldType.spec.fieldType).getOrElse(
        throw new AdaException(s"No ordering available for the field type ${fieldType.spec.fieldType}.")
      )
    }

    for {
      // prepare data set infos with initialized accessors and load fields
      dataSetInfos <- Future.sequence(spec.linkedDataSetSpecs.map(createDataSetInfo))

      // split into the left and right sides
      leftDataSetInfo = dataSetInfos.head
      rightDataSetInfos = dataSetInfos.tail

      // left link fields' ordering
      leftLinkOrdering = linkOrdering(leftDataSetInfo)

//      // right link fields' orderings
//      rightLinkOrderings = rightDataSetInfos.map(linkOrdering)


      // collect all the fields for the new data set
      newFields = {
        val rightFieldsWoLink = rightDataSetInfos.flatMap { rightDataSetInfo =>
          val fieldsWoLink = rightDataSetInfo.fields.filterNot{ _.name == rightDataSetInfo.linkFieldName }.toSeq

          if (spec.addDataSetIdToRightFieldNames)
            fieldsWoLink.map { field =>
              val newFieldName = rightDataSetInfo.dsa.dataSetId.replace('.', '_') + "-" + field.name
              field.copy(name = newFieldName)
            }
          else
            fieldsWoLink
        }

        leftDataSetInfo.fields ++ rightFieldsWoLink
      }

      // left stream
      leftStream <- createStream(leftDataSetInfo)

      // right streams
      rightSources <- Future.sequence(rightDataSetInfos.map(createStream))

      finalStream = {
        val rightIterators = (rightSources, rightDataSetInfos).zipped.map {
          case (source, info) =>
            val iterator = source.runWith(StreamConverters.asJavaStream[JsObject]).iterator()
            IteratorExt(iterator, info.linkFieldType)
        }

        var currentRightJson: Option[JsObject] = None
        var currentRightKey: Seq[Any] = Nil


        def compare(
          leftLink: Option[Any],
          rightLink: Option[Any]
        ): Int = leftLinkOrdering.compare(leftLink, rightLink)

        def findEqualLinkValues(
          info: LinkedDataSetInfo,
          fieldTypeMap: Map[String, FieldType[_]],
          linkOrderings: Seq[Ordering[Any]],
          jsonIterator: Iterator[JsObject]
        )
          (
            leftLink: Seq[Option[Any]]
          ) = {
          jsonIterator.takeWhile { rightJson =>
            val rightLink = rightJson.toValue(info.linkFieldType)

            val isEqual = (leftLink.zip(rightLink)).zip(linkOrderings).forall { case ((leftValue, rightValue), ordering: Ordering[Any]) =>
              ordering.equiv(leftValue, rightValue)
            }
            isEqual
          }
        }

        leftStream.mapConcat { leftJson =>
          val leftLink = leftJson.toValue(leftDataSetInfo.linkFieldType)

          if (leftLink.isDefined) {
            val rightJsons = rightIterators.map { rightIterator =>
              val lastRightLink = rightIterator.last.map(_.toValue(rightIterator.linkFieldType)).get

              leftLinkOrdering.compare(leftLink, lastRightLink) match {
                case 0 =>
                  Seq(rightIterator.last.get) ++ rightIterator.takeWhile { rightJson =>
                    val rightLink = rightJson.toValue(rightIterator.linkFieldType)
                    leftLinkOrdering.compare(leftLink, rightLink) == 0
                  }.toSeq

                case 1 =>
                  rightIterator.dropWhile { rightJson =>
                    val rightLink = rightJson.toValue(rightIterator.linkFieldType)
                    leftLinkOrdering.compare(leftLink, rightLink) == 1
                  }

                  rightIterator.takeWhile { rightJson =>
                    val rightLink = rightJson.toValue(rightIterator.linkFieldType)
                    leftLinkOrdering.compare(leftLink, rightLink) == 0
                  }.toSeq

                case -1 => Nil
              }
            }

            link(leftJson, rightJsons)
          } else
            List(leftJson)
        }
      }
    } yield {
      // if the left data set has all the fields preserved (i.e., no explicitly provided ones) then we save its views and filters
      val saveViewsAndFilters = spec.linkedDataSetSpecs.head.explicitFieldNamesToKeep.isEmpty

      (leftDataSetInfo.dsa, newFields, finalStream, saveViewsAndFilters)
    }
  }

  case class IteratorExt(
    iterator: java.util.Iterator[JsObject],
    linkFieldType: NamedFieldType[Any]
  ) extends Iterator[JsObject] {
    private var lastBuf: Option[JsObject] = None

    def last = lastBuf

    override def hasNext =
      iterator.hasNext

    override def next = {
      val value = iterator.next()
      lastBuf = Some(value)
      value
    }
  }

  private def createDataSetInfo(
    spec: SingleFieldLinkedDataSetSpec
  ): Future[LinkedDataSetInfo] = {
    // data set accessor
    val dsa = dsaSafe(spec.dataSetId)

    // determine which fields to load (depending on whether preserve field names are specified)
    val fieldNamesToLoad = spec.explicitFieldNamesToKeep match {
      case Nil => Nil
      case _ => (spec.explicitFieldNamesToKeep ++ Seq(spec.linkFieldName)).toSet
    }

    for {
      // load fields
      fields <- fieldNamesToLoad match {
        case Nil => dsa.fieldRepo.find()
        case _ => dsa.fieldRepo.find(Seq(FieldIdentity.name #-> fieldNamesToLoad.toSeq))
      }
    } yield {
      // collect field types (in order) for the link
      val nameFieldMap = fields.map(field => (field.name, field)).toMap

      val linkFieldType = nameFieldMap.get(spec.linkFieldName).map(_.toNamedTypeAny).getOrElse(
        throw new AdaException(s"Link field '${spec.linkFieldName}' not found.")
      )

      LinkedDataSetInfo(dsa, spec.linkFieldName, fields, linkFieldType)
    }
  }

  private def link(
    leftJson: JsObject,
    rightJsons: Traversable[Traversable[JsObject]]
  ): List[JsObject] = {
    val jsonId = (leftJson \ JsObjectIdentity.name).asOpt[BSONObjectID]

    // perform a cross-product of the right jsons
    val rightJsonsCrossed = crossProduct(rightJsons)

    if (rightJsonsCrossed.isEmpty) {
      List(leftJson)
    } else {
      rightJsonsCrossed.map { rightJsons =>
        val rightJson: JsObject = rightJsons.foldLeft(Json.obj()) {_ ++ _}
        val id = if (rightJsonsCrossed.size > 1 || jsonId.isEmpty) JsObjectIdentity.next else jsonId.get

        leftJson ++ rightJson ++ Json.obj(JsObjectIdentity.name -> id)
      }.toList
    }
  }

  case class LinkedDataSetInfo(
    dsa: DataSetAccessor,
    linkFieldName: String,
    fields: Traversable[Field],
    linkFieldType: NamedFieldType[Any]
  ) {
    val fieldNames = fields.map(_.name)
  }
}
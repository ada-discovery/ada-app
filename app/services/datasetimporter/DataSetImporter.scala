package services.datasetimporter

import java.nio.charset.{MalformedInputException, UnsupportedCharsetException, Charset}
import javax.inject.Inject

import dataaccess.{FieldTypeHelper, FieldType}
import models.{AdaParseException, DataSetImport}
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.JsObject
import play.api.Logger
import services.{DataSetService}
import util.MessageLogger
import scala.concurrent.Await._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.Source
import scala.concurrent.ExecutionContext.Implicits.global

trait DataSetImporter[T <: DataSetImport] {
  def apply(importInfo: T): Future[Unit]
}

private abstract class AbstractDataSetImporter[T <: DataSetImport] extends DataSetImporter[T] {

  @Inject var messageRepo: MessageRepo = _
  @Inject var dataSetService: DataSetService = _
  @Inject var dsaf: DataSetAccessorFactory = _

  protected val logger = Logger
  protected lazy val messageLogger = MessageLogger(logger, messageRepo)

  protected val fti = FieldTypeHelper.fieldTypeInferrer
  protected val ftf = FieldTypeHelper.fieldTypeFactory
  protected val defaultCharset = "UTF-8"
  protected val timeout = 2 minutes

  protected def createDataSetAccessor(importInfo: DataSetImport): DataSetAccessor =
    result(dsaf.register(
      importInfo.dataSpaceName,
      importInfo.dataSetId,
      importInfo.dataSetName,
      importInfo.setting
    ), timeout)

  protected def createJsonsWithFieldTypes(
    fieldNames: Seq[String],
    values: Seq[Seq[String]]
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = values.transpose.par.map(fti.apply).toList

    val jsons = values.map( vals =>
      JsObject(
        (fieldNames, fieldTypes, vals).zipped.map {
          case (fieldName, fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
            (fieldName, jsonValue)
        })
    )

    (jsons, fieldNames.zip(fieldTypes))
  }

  protected def createCsvFileLineIterator(
    path: String,
    charsetName: Option[String],
    eol: Option[String]
  ): Iterator[String] = {
    def createSource = {
      val charset = Charset.forName(charsetName.getOrElse(defaultCharset))
      Source.fromFile(path)(charset)
    }

    try {
      eol match {
        case Some(eol) => {
          // TODO: not effective... if a custom eol is used we need to read the whole file into memory and split again. It'd be better to use a custom BufferedReader
          createSource.mkString.split(eol).iterator
        }
        case None =>
          createSource.getLines
      }
    } catch {
      case e: UnsupportedCharsetException => throw AdaParseException(s"Unsupported charset '${charsetName.get}' detected.", e)
      case e: MalformedInputException => throw AdaParseException("Malformed input detected. It's most likely due to some special characters. Try a different chartset.", e)
    }
  }
}
package services.datasetimporter

import java.nio.charset.{Charset, MalformedInputException, UnsupportedCharsetException}
import java.text.DecimalFormat
import javax.inject.Inject

import dataaccess._
import field.{FieldType, FieldTypeHelper, FieldTypeInferrer}
import models.{AdaParseException, CsvDataSetImport, DataSetImport, FieldTypeSpec}
import persistence.RepoTypes._
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json.{JsNumber, JsObject, Json}
import play.api.Logger
import services.DataSetService
import util.{MessageLogger, seqFutures}

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

  protected val defaultFti = FieldTypeHelper.fieldTypeInferrer
  protected val ftf = FieldTypeHelper.fieldTypeFactory()
  protected val defaultCharset = "UTF-8"

  private val df = new DecimalFormat("#")
  df.setMaximumIntegerDigits(40)

  protected def createDataSetAccessor(
    importInfo: DataSetImport
  ): Future[DataSetAccessor] =
    for {
      dsa <- dsaf.register(
        importInfo.dataSpaceName,
        importInfo.dataSetId,
        importInfo.dataSetName,
        importInfo.setting,
        importInfo.dataView
      )

      _ <- dsa.updateDataSetRepo
    } yield
      dsa

  protected def createJsonsWithFieldTypes(
    fieldNames: Seq[String],
    values: Seq[Seq[String]],
    fti: Option[FieldTypeInferrer[String]] = None
  ): (Seq[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = values.transpose.par.map(fti.getOrElse(defaultFti).apply).toList

    val jsons = values.map( vals =>
      JsObject(
        (fieldNames, fieldTypes, vals).zipped.map {
          case (fieldName, fieldType, text) =>
            val jsonValue = fieldType.displayStringToJson(text)
//            if (text.contains("E+") || text.contains("e+")) {
//              val newJsNumber = JsNumber(BigDecimal(df.format(text.toDouble).toDouble))
//              println(newJsNumber)
//              println(fieldType.spec.fieldType + " : " + text + " -> " + jsonValue)
//            }
            (fieldName, jsonValue)
        })
    )

    (jsons, fieldNames.zip(fieldTypes))
  }

  protected def createJsonsWithStringFieldTypes(
    fieldNames: Seq[String],
    values: Iterator[Seq[String]]
  ): (Iterator[JsObject], Seq[(String, FieldType[_])]) = {
    val fieldTypes = fieldNames.map(_ => ftf.stringScalar)

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

  protected def saveStringsAndDictionaryWithoutTypeInference(
    dsa: DataSetAccessor,
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    saveBatchSize: Option[Int]
  ): Future[Unit] = {
    // create jsons and field types
    logger.info(s"Creating JSONs...")
    val (jsons, fieldNameAndTypes) = createJsonsWithStringFieldTypes(columnNames, values)

    for {
    // save, or update the dictionary
      _ <- {
        val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}
        dataSetService.updateDictionary(dsa.fieldRepo, fieldNameTypeSpecs, true, true)
      }

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetRepo

      // get the new data set repo
      dataRepo = dsa.dataSetRepo

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dsa.dataSetRepo.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        saveBatchSize match {
          case Some(saveBatchSize) =>
            seqFutures(
              jsons.grouped(saveBatchSize))(
              dataRepo.save
            )

          case None =>
            Future.sequence(
              jsons.map(dataRepo.save)
            )
        }
      }
    } yield
      ()
  }

  protected def saveStringsAndDictionaryWithTypeInference(
    dsa: DataSetAccessor,
    columnNames: Seq[String],
    values: Iterator[Seq[String]],
    saveBatchSize: Option[Int] = None,
    fti: Option[FieldTypeInferrer[String]] = None
  ): Future[Unit] = {
    // infer field types and create JSONSs
    logger.info(s"Inferring field types and creating JSONs...")

    val (jsons, fieldNameAndTypes) = createJsonsWithFieldTypes(columnNames, values.toSeq, fti)
    val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}

    saveJsonsAndDictionary(dsa, jsons, fieldNameTypeSpecs, saveBatchSize)
  }

  protected def saveJsonsAndDictionary(
    dsa: DataSetAccessor,
    jsons: Seq[JsObject],
    fieldNameAndTypeSpecs: Seq[(String, FieldTypeSpec)],
    saveBatchSize: Option[Int] = None
  ): Future[Unit] =
    for {
      // save, or update the dictionary
      _ <- dataSetService.updateDictionary(dsa.fieldRepo, fieldNameAndTypeSpecs, true, true)

      // since we possible changed the dictionary (the data structure) we need to update the data set repo
      _ <- dsa.updateDataSetRepo

      // get the new data set repo
      dataRepo = dsa.dataSetRepo

      // remove ALL the records from the collection
      _ <- {
        logger.info(s"Deleting the old data set...")
        dataRepo.deleteAll
      }

      // save the jsons
      _ <- {
        logger.info(s"Saving JSONs...")
        dataSetService.saveOrUpdateRecords(dataRepo, jsons,  None, false, None, saveBatchSize)
      }
    } yield
      ()
}
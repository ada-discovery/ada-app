package services.datasetimporter

import java.util.Date

import dataaccess.CategoryRepo._
import models.{Field, Category, FieldTypeSpec}
import models.{TranSmartDataSetImport, AdaParseException}
import reactivemongo.bson.BSONObjectID
import collection.mutable.{Map => MMap}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

private class TranSmartDataSetImporter extends AbstractDataSetImporter[TranSmartDataSetImport] {

  private val tranSmartDelimeter = '\t'
  private val tranSmartFieldGroupSize = 100

  override def apply(importInfo: TranSmartDataSetImport): Future[Unit] = {
    logger.info(new Date().toString)
    logger.info(s"Import of data set '${importInfo.dataSetName}' initiated.")

    val dataRepo = createDataSetAccessor(importInfo).dataSetRepo
    val delimiter = tranSmartDelimeter.toString

    try {
      val lines = createCsvFileLineIterator(
        importInfo.dataPath.get,
        importInfo.charsetName,
        None
      )

      // collect the column names
      val columnNames = dataSetService.getColumnNames(delimiter, lines)

      // parse lines
      logger.info(s"Parsing lines...")
      val values = dataSetService.parseLines(columnNames, lines, delimiter, false)

      // create jsons and field types
      val (jsons, fieldNameAndTypes) = createJsonsWithFieldTypes(columnNames, values.toSeq)

      for {
        // remove ALL the records from the collection
        _ <- {
          logger.info(s"Deleting the old data set...")
          dataRepo.deleteAll
        }

        // save the jsons
        _ <- {
          logger.info(s"Saving JSONs...")
          dataSetService.saveOrUpdateRecords(dataRepo, jsons)
        }

        // import the dictionary or save the inferred one
        _ <- {
          val fieldNameTypeSpecs = fieldNameAndTypes.map { case (fieldName, fieldType) => (fieldName, fieldType.spec)}

          if (importInfo.mappingPath.isDefined) {
            importTranSMARTDictionary(
              importInfo.dataSetId,
              tranSmartFieldGroupSize,
              tranSmartDelimeter.toString,
              createCsvFileLineIterator(
                importInfo.mappingPath.get,
                importInfo.charsetName,
                None
              ),
              fieldNameTypeSpecs
            )
          } else {
            dataSetService.updateDictionary(importInfo.dataSetId, fieldNameTypeSpecs, true, true)
          }
        }
      } yield {
        messageLogger.info(s"Import of data set '${importInfo.dataSetName}' successfully finished.")
      }
    } catch {
      case e: Exception => Future.failed(e)
    }
  }

  protected def importTranSMARTDictionary(
    dataSetId: String,
    fieldGroupSize: Int,
    delimiter: String,
    mappingFileLineIterator: => Iterator[String],
    fieldNameAndTypes: Seq[(String, FieldTypeSpec)]
  ): Future[Unit] = {
    logger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' initiated.")

    val dsa = dsaf(dataSetId).get
    val fieldRepo = dsa.fieldRepo
    val categoryRepo = dsa.categoryRepo

    // read the mapping file to obtain tupples: field name, field label, and category name; and a category name map
    val indexFieldNameMap: Map[Int, String] = fieldNameAndTypes.map(_._1).zipWithIndex.map(_.swap).toMap
    val (fieldNameLabelCategoryNameMap, nameCategoryMap) = createFieldNameAndCategoryNameMaps(mappingFileLineIterator, delimiter, indexFieldNameMap)

    for {
      // delete all the fields
      _ <- fieldRepo.deleteAll

      // delete all the categories
      _ <- categoryRepo.deleteAll

      // save the categories
      categoryIds: Traversable[(Category, BSONObjectID)] <- {
        val firstLayerCategories = nameCategoryMap.values.filter(!_.parent.isDefined)
        Future.sequence(
          firstLayerCategories.map(
            saveRecursively(categoryRepo, _)
          )
        ).map(_.flatten)
      }

      // save the fields... use a field label and a category from the mapping file provided, and infer a type
      _ <- {
        val categoryNameIdMap = categoryIds.map { case (category, id) => (category.name, id) }.toMap
        val groupedFieldNameAndTypes = fieldNameAndTypes.grouped(fieldGroupSize).toSeq
        val futures = groupedFieldNameAndTypes.par.map { fieldNameAndTypesGroup =>
          // Don't care about the result here, that's why we ignore ids and return Unit
          val idFutures = for ((fieldName, fieldTypeSpec) <- fieldNameAndTypesGroup) yield {
            val (fieldLabel, categoryName) = fieldNameLabelCategoryNameMap.getOrElse(fieldName, ("", None))
            val categoryId = categoryName.map(categoryNameIdMap.get(_).get)
            val stringEnums = fieldTypeSpec.enumValues.map(_.map { case (from, to) => (from.toString, to)})
            fieldRepo.save(Field(fieldName, Some(fieldLabel), fieldTypeSpec.fieldType, fieldTypeSpec.isArray, stringEnums, None, None, None, Nil, categoryId))
          }
          Future.sequence(idFutures)
        }
        Future.sequence(futures.toList)
      }
    } yield
      messageLogger.info(s"TranSMART dictionary inference and import for data set '${dataSetId}' successfully finished.")
  }

  private def createFieldNameAndCategoryNameMaps(
    lineIterator: => Iterator[String],
    delimiter: String,
    indexFieldNameMap: Map[Int, String]
  ): (Map[String, (String, Option[String])], Map[String, Category]) = {
    val nameCategoryMap = MMap[String, Category]()

    val fieldNameLabelCategoryNameMap = lineIterator.drop(1).zipWithIndex.map { case (line, index) =>
      val values = dataSetService.parseLine(delimiter, line)
      if (values.size != 4)
        throw new AdaParseException(s"TranSMART mapping file contains a line (index '$index') with '${values.size}' items, but 4 expected (filename, category, column number, and data label). Parsing terminated.")

      val filename	= values(0).trim
      val categoryCD	= values(1).trim
      val colNumber	= values(2).trim.toInt
      val fieldLabel = values(3).trim

      val fieldName = indexFieldNameMap.get(colNumber - 1).getOrElse(
        throw new AdaParseException(s"TranSMART mapping file contains an invalid reference to a non-existing column '$colNumber.' Parsing terminated.")
      )

      // collect all categories
      val categoryNames = if (categoryCD.nonEmpty)
        categoryCD.split("\\+").map(_.trim.replaceAll("_", " "))
      else
        Array[String]()

      val assocCategoryName = if (categoryNames.nonEmpty) {
        val categories = categoryNames.map(categoryName =>
          nameCategoryMap.getOrElseUpdate(categoryName, new Category(categoryName))
        )
        categories.sliding(2).foreach { adjCategories =>
          val parent = adjCategories(0)
          val child = adjCategories(1)
          if (!parent.children.contains(child))
            parent.addChild(child)
        }
        Some(categoryNames.last)
      } else
        None

      (fieldName, (fieldLabel, assocCategoryName))
    }.toMap
    (fieldNameLabelCategoryNameMap, nameCategoryMap.toMap)
  }
}

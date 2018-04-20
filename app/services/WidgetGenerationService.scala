package services

import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.google.inject.ImplementedBy
import dataaccess.{Criterion, FieldType, FieldTypeHelper, NotEqualsNullCriterion}
import dataaccess.RepoTypes.JsonReadonlyRepo
import models._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import services.stats.StatsService
import services.stats.calc.{NumericDistributionFlowOptions, NumericDistributionOptions, UniqueDistributionCountsCalcTypePack}
import services.widgetgen._
import services.stats.CalculatorHelper._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[WidgetGenerationServiceImpl])
trait WidgetGenerationService {

  def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]] = Nil,
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]] = Map(),
    fields: Traversable[Field],
    usePerWidgetRepoMethod: Boolean = false
  ): Future[Traversable[Option[(Widget, Seq[String])]]]

  def genLocally(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    fields: Traversable[Field]
  ): Option[Widget]

  def genStreamed(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]]

  def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]]
}

@Singleton
class WidgetGenerationServiceImpl @Inject() (
    statsService: StatsService
  ) extends WidgetGenerationService {

  private implicit val ftf = FieldTypeHelper.fieldTypeFactory()
  private val maxIntCountsForZeroPadding = 1000
  private val defaultNumericBinCount = 20
  private val streamedCorrelationCalcParallelism = Some(4)

  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  import statsService._

  override def apply(
    widgetSpecs: Traversable[WidgetSpec],
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    widgetFilterSubCriteriaMap: Map[BSONObjectID, Seq[Criterion[Any]]],
    fields: Traversable[Field],
    usePerWidgetRepoMethod: Boolean
  ): Future[Traversable[Option[(Widget, Seq[String])]]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap

    // a helper function to get the sub criteria for the widget
    def getSubCriteria(filterId: Option[BSONObjectID]): Seq[Criterion[Any]] =
      filterId.map(subFilterId =>
        widgetFilterSubCriteriaMap.get(subFilterId).getOrElse(Nil)
      ).getOrElse(Nil)

    val splitWidgetSpecs: Traversable[Either[WidgetSpec, WidgetSpec]] =
      if (usePerWidgetRepoMethod)
        widgetSpecs.collect {
          case p: DistributionWidgetSpec => Left(p)
          case p: BoxWidgetSpec => Left(p)
          case p: CumulativeCountWidgetSpec => if (p.numericBinCount.isDefined) Left(p) else Right(p)
          case p: ScatterWidgetSpec => Right(p)
          case p: CorrelationWidgetSpec => Right(p)
          case p: BasicStatsWidgetSpec => Right(p)
          case p: TemplateHtmlWidgetSpec => Left(p)
        }
      else
        widgetSpecs.map(Right(_))

    val repoWidgetSpecs = splitWidgetSpecs.map(_.left.toOption).flatten
    val fullDataWidgetSpecs = splitWidgetSpecs.map(_.right.toOption).flatten
    val fullDataFieldNames = fullDataWidgetSpecs.map(_.fieldNames).flatten.toSet

    println("Loaded chart field names: " + fullDataFieldNames.mkString(", "))

    val repoWidgetSpecsFuture =
      Future.sequence(
        repoWidgetSpecs.par.map { widgetSpec =>

          val newCriteria = criteria ++ getSubCriteria(widgetSpec.subFilterId)
          genFromRepoAux(widgetSpec, repo, newCriteria, nameFieldMap).map { widget =>
            val widgetFieldNames = widget.map(widget => (widget, widgetSpec.fieldNames.toSeq))
            (widgetSpec, widgetFieldNames)
          }
        }.toList
      )

    val fullDataWidgetSpecsFuture =
      if (fullDataWidgetSpecs.nonEmpty) {
        Future.sequence(
          fullDataWidgetSpecs.groupBy(_.subFilterId).map { case (subFilterId, widgetSpecs) =>
            val fieldNames = widgetSpecs.map(_.fieldNames).flatten.toSet

            repo.find(criteria ++ getSubCriteria(subFilterId), Nil, fieldNames).map { chartData =>
              widgetSpecs.par.map { widgetSpec =>
                val widget = genLocally(widgetSpec, chartData, fields)
                val widgetFieldNames = widget.map(widget => (widget, widgetSpec.fieldNames.toSeq))
                (widgetSpec, widgetFieldNames)
              }.toList
            }
          }
        )
      } else
        Future(Nil)

    for {
      chartSpecs1 <- repoWidgetSpecsFuture
      chartSpecs2 <- fullDataWidgetSpecsFuture
    } yield {
      val specWidgetMap = (chartSpecs1 ++ chartSpecs2.flatten).toMap
      // return widgets in the specified order
      widgetSpecs.map(specWidgetMap.get).flatten
    }
  }

  override def genFromRepo(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genFromRepoAux(widgetSpec, repo, criteria, nameFieldMap)
  }

  private def genFromRepoAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Future[Option[Widget]] = {

    // aux functions to retrieve a field by name
    def getField(name: String): Field =
      nameFieldMap.get(name).getOrElse(throw new AdaException(s"Field $name not found."))

    def getFieldO(name: Option[String]) = name.map(getField)

    widgetSpec match {

      case spec: DistributionWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        calcDistributionCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map(
          DistributionWidgetGenerator(nameFieldMap, spec)
        )

      case spec: CumulativeCountWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)
        calcCumulativeCountsFromRepo(repo, criteria, field, groupField, spec.numericBinCount).map(
          CumulativeCountWidgetGenerator2(nameFieldMap, spec)
        )

      case spec: BoxWidgetSpec =>
        calcQuartilesFromRepo(repo, criteria, getField(spec.fieldName)).map(
          _.flatMap(BoxWidgetGenerator(nameFieldMap, spec))
        )

      case spec: TemplateHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
  }

  override def genLocally(
    widgetSpec: WidgetSpec,
    items: Traversable[JsObject],
    fields: Traversable[Field]
  ): Option[Widget] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genLocallyAux(widgetSpec, items, nameFieldMap)
  }

  private def genLocallyAux(
    widgetSpec: WidgetSpec,
    jsons: Traversable[JsObject],
    nameFieldMap: Map[String, Field]
  ): Option[Widget] = {

    // aux functions to retrieve a field by name
    def getField(name: String): Field =
      nameFieldMap.get(name).getOrElse(throw new AdaException(s"Field $name not found."))

    def getFieldO(name: Option[String]) = name.map(getField)

    val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq

    widgetSpec match {

      case spec: DistributionWidgetSpec =>
        val field = getField(spec.fieldName)
        val groupField = getFieldO(spec.groupFieldName)

        val countSeries = calcDistributionCounts(jsons, field, groupField, spec.numericBinCount)
        DistributionWidgetGenerator(nameFieldMap, spec)(countSeries)

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty =>
        val options = NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))
        val counts = cumulativeNumericBinCountsSeqExec.execJsonA(options, fields(0), fields)(jsons)
        CumulativeNumericBinCountWidgetGenerator(nameFieldMap, spec)(counts)

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined =>
        val options = NumericDistributionOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount))
        val counts = groupCumulativeNumericBinCountsSeqExec.execJsonA(options, fields(1), fields)(jsons)
        GroupCumulativeNumericBinCountWidgetGenerator(nameFieldMap, spec)(counts)

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isEmpty =>
        val valueCounts = cumulativeOrderedCountsAnySeqExec.execJsonA_(fields(0), fields)(jsons)
        CumulativeCountWidgetGenerator(nameFieldMap, spec)(valueCounts)

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isDefined =>
        val groupCounts = groupCumulativeOrderedCountsAnySeqExec[Any].execJsonA_(fields(1), fields)(jsons)
        GroupCumulativeCountWidgetGenerator(nameFieldMap, spec)(groupCounts)

      case spec: BoxWidgetSpec =>
        quartilesAnySeqExec.execJsonA_(fields(0), fields)(jsons).flatMap(
          BoxWidgetGenerator(nameFieldMap, spec)
        )

      case spec: ScatterWidgetSpec if spec.groupFieldName.isDefined =>
        val data = groupTupleSeqExec[String, Any, Any].execJson_(fields)(jsons)
        GroupScatterWidgetGenerator[Any, Any](nameFieldMap, spec)(data)

      case spec: ScatterWidgetSpec if spec.groupFieldName.isEmpty =>
        val data = tupleSeqExec[Any, Any].execJson_(fields)(jsons)
        ScatterWidgetGenerator[Any, Any](nameFieldMap, spec)(data)

      case spec: CorrelationWidgetSpec =>
        val correlations = pearsonCorrelationExec.execJson((), fields)(jsons)
        CorrelationWidgetGenerator(nameFieldMap, spec)(correlations)

      case spec: BasicStatsWidgetSpec =>
        val results = basicStatsSeqExec.execJsonA_(fields(0), fields)(jsons)
        BasicStatsWidgetGenerator(nameFieldMap, spec)(results)

      case spec: TemplateHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Some(widget)
    }
  }

  override def genStreamed(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    fields: Traversable[Field]
  ): Future[Option[Widget]] = {
    val nameFieldMap = fields.map(field => (field.name, field)).toMap
    genStreamedAux(widgetSpec, repo, criteria, nameFieldMap)
  }

  private def genStreamedAux(
    widgetSpec: WidgetSpec,
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    nameFieldMap: Map[String, Field]
  ): Future[Option[Widget]] = {
    val fields = widgetSpec.fieldNames.flatMap(nameFieldMap.get).toSeq

    widgetSpec match {

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isEmpty =>
        val options = NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), 0, 1)
        cumulativeNumericBinCountsSeqExec.execJsonRepoStreamedA(options, options, true, fields(0), fields)(repo, criteria).map(
          CumulativeNumericBinCountWidgetGenerator(nameFieldMap, spec)
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isDefined && spec.groupFieldName.isDefined =>
        val options = NumericDistributionFlowOptions(spec.numericBinCount.getOrElse(defaultNumericBinCount), 0, 1)
        groupCumulativeNumericBinCountsSeqExec.execJsonRepoStreamedA(options, options, true, fields(1), fields)(repo, criteria).map(
          GroupCumulativeNumericBinCountWidgetGenerator(nameFieldMap, spec)
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isEmpty =>
        cumulativeOrderedCountsAnySeqExec.execJsonRepoStreamedA_(true, fields(0), fields)(repo, criteria).map(
          CumulativeCountWidgetGenerator(nameFieldMap, spec)
        )

      case spec: CumulativeCountWidgetSpec if spec.numericBinCount.isEmpty && spec.groupFieldName.isDefined =>
        groupCumulativeOrderedCountsAnySeqExec[Any].execJsonRepoStreamedA_(true, fields(1), fields)(repo, criteria).map(
          GroupCumulativeCountWidgetGenerator(nameFieldMap, spec)
        )

      case spec: BoxWidgetSpec =>
        quartilesAnySeqExec.execJsonRepoStreamedA_(true, fields(0), fields)(repo, criteria ++ withNotNull(fields)).map(
          _.flatMap(BoxWidgetGenerator(nameFieldMap, spec))
        )

      case spec: ScatterWidgetSpec if spec.groupFieldName.isDefined =>
        groupTupleSeqExec[String, Any, Any].execJsonRepoStreamed_(true, fields)(repo, criteria ++ withNotNull(fields.tail)).map(
          GroupScatterWidgetGenerator[Any, Any](nameFieldMap, spec)
        )

      case spec: ScatterWidgetSpec if spec.groupFieldName.isEmpty =>
        tupleSeqExec[Any, Any].execJsonRepoStreamed_(true, fields)(repo, criteria ++ withNotNull(fields)).map(
          ScatterWidgetGenerator[Any, Any](nameFieldMap, spec)
        )

      case spec: CorrelationWidgetSpec =>
        pearsonCorrelationExec.execJsonRepoStreamed(
          streamedCorrelationCalcParallelism,
          streamedCorrelationCalcParallelism,
          true, fields)(repo, criteria
        ).map(
          CorrelationWidgetGenerator(nameFieldMap, spec)
        )

      case spec: BasicStatsWidgetSpec =>
        basicStatsSeqExec.execJsonRepoStreamedA_(true, fields(0), fields)(repo, criteria).map(
          BasicStatsWidgetGenerator(nameFieldMap, spec)
        )

      case spec: TemplateHtmlWidgetSpec =>
        val widget = HtmlWidget("", spec.content, spec.displayOptions)
        Future(Some(widget))

      case _ => Future(None)
    }
  }

  private def withNotNull(fields: Seq[Field]): Seq[Criterion[Any]] =
    fields.map(field => NotEqualsNullCriterion(field.name))

//  private def withNotNull(fields: Field*): Seq[Criterion[Any]] =
//    fields.map(field => NotEqualsNullCriterion(field.name))

  private def calcDistributionCountsFromRepo(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Traversable[(String, Traversable[Count[Any]])]] =
    groupField match {
      case Some(groupField) =>
        field.fieldType match {

          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            calcGroupedNumericDistributionCountsFromRepo(repo, criteria, field, groupField, numericBinCount).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              createGroupNumericCounts(groupCounts, groupFieldType, field)
            }

          case _ =>
            calcGroupedUniqueDistributionCountsFromRepo(repo, criteria, field, groupField).map { groupCounts =>
              val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
              val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

              createGroupStringCounts(groupCounts, groupFieldType, fieldType)
            }
        }

      case None =>
        field.fieldType match {
          case FieldTypeId.Double | FieldTypeId.Integer | FieldTypeId.Date =>
            calcNumericDistributionCountsFromRepo(repo, criteria, field, numericBinCount).map { numericCounts =>
              val counts = createNumericCounts(numericCounts, convertNumeric(field.fieldType))
              Seq(("All", counts))
            }

          case _ =>
            calcUniqueDistributionCountsFromRepo(repo, criteria, field).map { counts =>
              val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
              Seq(("All", createStringCounts(counts, fieldType)))
            }
        }
    }

  private def convertNumeric(fieldType: FieldTypeId.Value) =
    fieldType match {
      case FieldTypeId.Date =>
        val convert = {ms: BigDecimal => new java.util.Date(ms.setScale(0, BigDecimal.RoundingMode.CEILING).toLongExact)}
        Some(convert)
      case _ => None
    }

  private def calcDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Traversable[(String, Traversable[Count[Any]])] =
    groupField match {
      case Some(groupField) =>
        field.fieldType match {
          // group numeric
          case FieldTypeId.Double | FieldTypeId.Date =>
            countGroupNumericDistributionCounts(items, field, groupField, numericBinCount)

          // group numeric or unique depending on whether bin counts are defined
          case FieldTypeId.Integer =>
            numericBinCount match {

              case Some(_) =>
                countGroupNumericDistributionCounts(items, field, groupField, numericBinCount)

              case None =>
                countGroupUniqueIntDistributionCounts(items, field,  groupField)
            }

          // group unique
          case _ => countGroupUniqueDistributionCounts(items, field, groupField)
        }

      case None =>
        val counts = field.fieldType match {
          // numeric
          case FieldTypeId.Double | FieldTypeId.Date =>
            countNumericDistributionCounts(items, field, numericBinCount)

          // numeric or unique depending on whether bin counts are defined
          case FieldTypeId.Integer =>
            numericBinCount match {

              case Some(_) =>
                countNumericDistributionCounts(items, field, numericBinCount)

              case None =>
                countUniqueIntDistributionCounts(items, field)
            }

          // unique
          case _ => countUniqueDistributionCounts(items, field)
        }

        Seq(("All", counts))
    }

  private def countGroupNumericDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field,
    numericBinCount: Option[Int]
  ) = {
    val options = NumericDistributionOptions(numericBinCount.getOrElse(defaultNumericBinCount))
    val groupCounts = groupNumericDistributionCountsExec[Any].execJsonA(options, field, (groupField, field))(items)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
    createGroupNumericCounts(groupCounts, groupFieldType, field)
  }

  private def countGroupUniqueDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val groupCounts = groupUniqueDistributionCountsExec[Any, Any].execJsonA_(field, (groupField, field))(items)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]

    createGroupStringCounts(groupCounts, groupFieldType, fieldType)
  }

  private def countGroupUniqueIntDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    groupField: Field
  ) = {
    val longGroupCounts = groupUniqueDistributionCountsExec[Any, Long].execJsonA_(field, (groupField, field))(items)
    val groupFieldType = ftf(groupField.fieldTypeSpec).asValueOf[Any]

    toGroupStringValues(longGroupCounts, groupFieldType).map {
      case (groupString, valueCounts) =>
        (groupString, prepareIntCounts(valueCounts))
    }
  }

  private def countNumericDistributionCounts(
    items: Traversable[JsObject],
    field: Field,
    numericBinCount: Option[Int]
  ) = {
    val options = NumericDistributionOptions(numericBinCount.getOrElse(defaultNumericBinCount))
    val counts = numericDistributionCountsExec.execJsonA_(options, field)(items)
    createNumericCounts(counts, convertNumeric(field.fieldType))
  }

  private def countUniqueDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val uniqueCounts = uniqueDistributionCountsExec[Any].execJsonA_((), field)(items)
    val fieldType = ftf(field.fieldTypeSpec).asValueOf[Any]
    createStringCounts(uniqueCounts, fieldType)
  }

  private def countUniqueIntDistributionCounts(
    items: Traversable[JsObject],
    field: Field
  ) = {
    val longUniqueCounts = uniqueDistributionCountsExec[Long].execJsonA_((), field)(items)
    prepareIntCounts(longUniqueCounts)
  }

  private def prepareIntCounts(
    longUniqueCounts: UniqueDistributionCountsCalcTypePack[Long]#OUT
  ) = {
    val counts = longUniqueCounts.collect{ case (Some(value), count) => Count(value, count)}.toSeq.sortBy(_.value)
    val size = counts.size

    val min = counts.head.value
    val max = counts.last.value

    // if the difference between max and min is "small" enough we can add a zero count for missing values
    if (Math.abs(max - min) < maxIntCountsForZeroPadding) {
      val countsWithZeroes = for (i <- 0 to size - 1) yield {
        val count = counts(i)
        if (i + 1 < size) {
          val nextCount = counts(i + 1)

          val zeroCounts =
            for (missingValue <- count.value + 1 to nextCount.value - 1) yield Count(missingValue, 0)

          Seq(count) ++ zeroCounts
        } else
          Seq(count)
      }

      countsWithZeroes.flatten
    } else
      counts
  }

  private def createStringCounts[T](
    counts: Traversable[(Option[T], Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.map(_.toString)
      val label = value.map(value => fieldType.valueToDisplayString(Some(value))).getOrElse("Undefined")
      Count(label, count, stringKey)
    }

  private def createStringCountsDefined[T](
    counts: Traversable[(T, Int)],
    fieldType: FieldType[T]
  ): Traversable[Count[String]] =
    counts.map { case (value, count) =>
      val stringKey = value.toString
      val label = fieldType.valueToDisplayString(Some(value))
      Count(label, count, Some(stringKey))
    }

  private def createNumericCounts(
    counts: Traversable[(BigDecimal, Int)],
    convert: Option[BigDecimal => Any] = None
  ): Seq[Count[_]] =
    counts.toSeq.sortBy(_._1).map { case (xValue, count) =>
      val convertedValue = convert.map(_.apply(xValue)).getOrElse(xValue.toDouble)
      Count(convertedValue, count, None)
    }

  private def createGroupStringCounts[G, T](
    groupCounts: Traversable[(Option[G], Traversable[(Option[T], Int)])],
    groupFieldType: FieldType[G],
    fieldType: FieldType[T]
  ): Seq[(String, Traversable[Count[String]])] =
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createStringCounts(counts, fieldType))
    }

  private def createGroupNumericCounts[G](
    groupCounts: Traversable[(Option[G], Traversable[(BigDecimal, Int)])],
    groupFieldType: FieldType[G],
    field: Field
  ): Seq[(String, Traversable[Count[_]])] = {
    // value converter
    val convert = convertNumeric(field.fieldType)

    // handle group string names and convert values
    toGroupStringValues(groupCounts, groupFieldType).map { case (groupString, counts) =>
      (groupString, createNumericCounts(counts, convert))
    }
  }

  private def toGroupStringValues[G, T](
    groupCounts: Traversable[(Option[G], Traversable[T])],
    groupFieldType: FieldType[G]
  ): Seq[(String, Traversable[T])] =
    groupCounts.toSeq.sortBy(_._1.isEmpty).map { case (group, values) =>
      val groupString = group match {
        case Some(group) => groupFieldType.valueToDisplayString(Some(group))
        case None => "Undefined"
      }
      (groupString, values)
    }

  private def calcCumulativeCountsFromRepo(
    repo: JsonReadonlyRepo,
    criteria: Seq[Criterion[Any]],
    field: Field,
    groupField: Option[Field],
    numericBinCount: Option[Int]
  ): Future[Traversable[(String, Traversable[Count[Any]])]] =
    numericBinCount match {
      case Some(_) =>
        calcDistributionCountsFromRepo(repo, criteria, field, groupField, numericBinCount).map( distCountsSeries =>
          toCumCounts(distCountsSeries.toSeq)
        )

      case None =>
        Future(Nil)
    }

  // function that converts dist counts to cumulative counts by applying simple running sum
  private def toCumCounts[T](
    distCountsSeries: Traversable[(String, Traversable[Count[T]])]
  ): Traversable[(String, Seq[Count[T]])] =
    distCountsSeries.map { case (seriesName, distCounts) =>
      val distCountsSeq = distCounts.toSeq
      val cumCounts = distCountsSeq.scanLeft(0) { case (sum, count) =>
        sum + count.count
      }
      val labeledDistCounts: Seq[Count[T]] = distCountsSeq.map(_.value).zip(cumCounts.tail).map { case (value, count) =>
        Count(value, count)
      }
      (seriesName, labeledDistCounts)
    }
}
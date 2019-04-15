package runnables.mpower

import javax.inject.Inject

import org.ada.server.models.{Field, FieldTypeId, StorageType}
import org.ada.server.AdaException
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import play.api.libs.json._
import services.DataSetService
import controllers.mpower._
import org.ada.server.models.DataSetFormattersAndIds.FieldIdentity
import org.incal.core.InputFutureRunnable
import org.incal.core.dataaccess.Criterion._
import org.incal.core.util.{seqFutures, GroupMapList}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf

class CalcCorrelationAggregates @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[CalcCorrelationAggregatesSpec] with FeatureMatrixExtractor {

  private implicit val ldopaScoreSubmissionFormat = Json.format[LDOPAScoreSubmissionInfo]
  private implicit val mPowerScoreSubmissionFormat = Json.format[mPowerScoreSubmissionInfo]
  private implicit val featureInfoFormat = Json.format[FeatureInfo]

  private val submissionIdField = Field("submissionId", Some("Submission Id"), FieldTypeId.Integer)

  private val featureGroupSize = Some(200)
  private val maxCorrelation = 1d

  private val logger = Logger

  private val aggFunWithNames = Seq(
    (aggFun.max, "max"),
    (aggFun.min, "min"),
    (aggFun.mean, "mean")
  )

  private val aggOutInProduct = for {
    aggOut <- aggFunWithNames
    aggIn <- aggFunWithNames
  } yield
    (aggOut, aggIn)

  override def runAsFuture(spec: CalcCorrelationAggregatesSpec) = {
    val scoreBoardDsa = dsaf(spec.scoreBoardDataSetId).get
    val correlationDsa = dsaf(spec.correlationDataSetId).get
    val featureInfoDsa = dsaf(spec.featureInfoDataSetId).get

    for {
      // get the name of the source correlation data set
      correlationSetName <- correlationDsa.dataSetName

      // retrieve the demographic features
      submissionIdDemographicFeaturesMap <- groupDemographicFeaturesBySubmission(featureInfoDsa)

      // get all the scored submission infos
      submissionInfos <- scoreBoardDsa.dataSetRepo.find().map(jsons =>
        jsons.map( json =>
          json.asOpt[LDOPAScoreSubmissionInfo].getOrElse(json.as[mPowerScoreSubmissionInfo])
        )
      )

      // create a submission id feature names map
      submissionIdFeatureNamesMap <- createSubmissionIdFeatureMap(correlationDsa)

      // calc aggregates and store a resulting data set for all the possible combinations
      _ <- seqFutures(aggOutInProduct) { case ((aggOut, aggOutName), (aggIn, aggInName)) =>

        val run = runAux(submissionInfos, submissionIdFeatureNamesMap, correlationDsa, aggOut, aggIn)_

        val targetDataSetId = s"${spec.correlationDataSetId}_abs_${aggOutName}_${aggInName}"
        val targetDataSetName = s"${correlationSetName} Abs ${aggOutName.capitalize} ${aggInName.capitalize}"

        for {
          // run as it is
          _ <- run(targetDataSetId, targetDataSetName, Map())

          // run with demographic feature exclusion
          _ <- run(targetDataSetId + "_wo_dem", targetDataSetName + " WO Demographics", submissionIdDemographicFeaturesMap)
        } yield
          ()
      }
    } yield
      ()
  }

  private def runAux(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    correlationDsa: DataSetAccessor,
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double])(
    newDataSetId: String,
    newDataSetName: String,
    submissionFeaturesToExclude: Map[Int, Set[String]]
  ) =
    for {
      // create new jsons with aggregates and fields
      (jsons, fields) <- {
        calcCrossSubmissionAggregates(submissionInfos, submissionIdFeatureNamesMap, correlationDsa, submissionFeaturesToExclude, aggOut, aggIn, Some(_.abs)).map { aggregates =>
          val uniAggs1 = aggregates.map { case (sub1, sub2, agg1, _) => (sub1, (sub2, agg1))}
          val uniAggs2 = aggregates.map { case (sub1, sub2, _, agg2) => (sub2, (sub1, agg2))}

          val uniAggsGrouped = (uniAggs1 ++ uniAggs2).toGroupMap.toSeq.sortBy(_._1)

          // create jsons
          val jsons = uniAggsGrouped.map { case (submissionId, aggregates) =>
            val sortedAggregates = aggregates.toSeq.sortBy(_._2)
            val jsValues =
              sortedAggregates.map { case (submissionId2, agg) =>
                submissionId2.toString -> agg.map { value => JsNumber(Math.min(maxCorrelation, value)) }.getOrElse(JsNull)
              }

            JsObject(
              Seq(
                submissionIdField.name -> JsNumber(submissionId),
                submissionId.toString -> JsNumber(maxCorrelation)
              ) ++ jsValues
            )
          }

          // create fields
          val fields = uniAggsGrouped.map { case (submissionId, _) =>
            Field(submissionId.toString, None, FieldTypeId.Double, false)
          }

          (jsons, fields)
        }
      }

      // register the target dsa
      targetDsa <- dataSetService.register(
        correlationDsa,
        newDataSetId,
        newDataSetName,
        StorageType.ElasticSearch
      )

      // create a new dictionary
      _ <- dataSetService.updateDictionaryFields(newDataSetId, fields ++ Seq(submissionIdField), false, true)

      // delete the old data (if any)
      _ <- targetDsa.dataSetRepo.deleteAll

      // save the new data
      _ <- targetDsa.dataSetRepo.save(jsons)
    } yield
      ()

  private def calcCrossSubmissionAggregates(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    featureMatrixDsa: DataSetAccessor,
    submissionFeaturesToExclude: Map[Int, Set[String]],
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double],
    valueTransform: Option[Double => Double] = None
  ): Future[Seq[(Int, Int, Option[Double], Option[Double])]] =
    for {
      // calculate aggregations between each pair of submissions
      crossSubmissionAggregates <- {
        val filteredFeatureNamesMap =
          submissionIdFeatureNamesMap.map { case (submissionId, features) =>
            val featuresToExclude = submissionFeaturesToExclude.get(submissionId).getOrElse(Set())
            val newFeatures = features.filterNot(featuresToExclude.contains)

            assert(
              featuresToExclude.size + newFeatures.size == features.size,
                s"The number of features after exclusion is inconsistent." +
                s"There must be some features that do not occur in the original feature set." +
                s"Counts: ${features.size} - ${featuresToExclude.size} != ${newFeatures.size}.\n" +
                s"Features to exclude: ${featuresToExclude.mkString(",")}\n" +
                s"Actual features: ${features.mkString(",")}"
            )

            (submissionId, newFeatures)
          }
        calcAggregatesForAllSubmissions(submissionInfos, filteredFeatureNamesMap, featureMatrixDsa, aggOut, aggIn, valueTransform)
      }
    } yield
      crossSubmissionAggregates

  private def calcAggregatesForAllSubmissions(
    submissionInfos: Traversable[SubmissionInfo],
    submissionIdFeatureNamesMap: Map[Int, Traversable[String]],
    featureMatrixDsa: DataSetAccessor,
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double],
    valueTransform: Option[Double => Double] = None
  ): Future[Seq[(Int, Int,  Option[Double], Option[Double])]] = {
    val definedSubmissionInfos = submissionInfos.filter(_.submissionIdInt.isDefined)

    logger.info(s"Calculating aggregates for ${submissionInfos.size} submissions.")

    seqFutures(definedSubmissionInfos) { submissionInfo1 =>
      val submissionId1 = submissionInfo1.submissionIdInt.get
      val submissionInfos2 = definedSubmissionInfos.filter(subInfo => submissionId1 < subInfo.submissionIdInt.get)

      logger.info(s"Calculating aggregates for the submission ${submissionId1}.")

      seqFutures(submissionInfos2) { submissionInfo2 =>
        val submissionId2 = submissionInfo2.submissionIdInt.get
        calcAggregatesForIdPair(submissionId1, submissionId2, submissionIdFeatureNamesMap, featureMatrixDsa, featureGroupSize, aggOut, aggIn, valueTransform)
      }
    }.map(_.flatten)
  }

  private def createSubmissionIdFeatureMap(
    correlationDsa: DataSetAccessor
  ): Future[Map[Int, Traversable[String]]] =
    for {
    // get all the feature names
      featureNames <- correlationDsa.fieldRepo.find(Seq(FieldIdentity.name #!= featureFieldName)).map(fields =>
        fields.map(_.name)
      )
    } yield
      // create a submission id feature names map
      featureNames.map{ featureName =>
        val featureNameParts = featureName.split("-", 2)
        val submissionId = featureNameParts(0).toInt
        (submissionId, featureName)
      }.toGroupMap

  private def groupDemographicFeaturesBySubmission(
    featureInfoDsa: DataSetAccessor
  ): Future[Map[Int, Set[String]]] =
    for {
      categoryField <- featureInfoDsa.fieldRepo.get("Category")

      demographicFeatureInfos <- {
        val field = categoryField.getOrElse(throw new AdaException("Field Category not found"))
        field.numValues.get.find(_._2.equals("demographic")).map(_._1.toInt) match {
          case Some(demographicValue) =>
            featureInfoDsa.dataSetRepo.find(
              criteria = Seq("Category" #== demographicValue)
            ).map(_.map(_.as[FeatureInfo]))

          case None => Future(Nil)
        }
      }
    } yield
      demographicFeatureInfos.groupBy(_.SubmissionId).map { case (submissionId, values) =>
        // need to add submissionId prefix with "-" because that's how the features are stored
        (submissionId, values.map(featureInfo => submissionId + "-" + featureInfo.Name).toSet)
      }

  override def inputType = typeOf[CalcCorrelationAggregatesSpec]
}

case class CalcCorrelationAggregatesSpec(
  correlationDataSetId: String,
  scoreBoardDataSetId: String,
  featureInfoDataSetId: String
)

trait FeatureMatrixExtractor {

  protected val featureFieldName = "featureName"

  object aggFun {
    val max = aggAux(_.max)(_)
    val min = aggAux(_.min)(_)
    val mean = aggAux(values => values.sum / values.size)(_)

    private def aggAux(
                        agg: Seq[Double] => Double)(
                        values: Seq[Option[Double]]
                      ) =
      values.flatten match {
        case Nil => None
        case flattenedValues => Some(agg(flattenedValues))
      }
  }

  protected def calcAggregatesForIdPair[T](
    id1: T,
    id2: T,
    idFeatureNamesMap: Map[T, Traversable[String]],
    featureMatrixDsa: DataSetAccessor,
    groupSize: Option[Int],
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double],
    valueTransform: Option[Double => Double] = None
  ): Future[(T, T,  Option[Double], Option[Double])] = {

    // aux function to find feature names for given ids
    def findFeatureNames(id: T): Traversable[String] =
      idFeatureNamesMap.get(id).get

    val featureNames1 = findFeatureNames(id1).toSeq
    val featureNames2 = findFeatureNames(id2).toSeq

    for {
      aggregates <- extractFeatureMatrixAggregates(
        featureNames1, featureNames2, featureMatrixDsa, groupSize, aggOut, aggIn, valueTransform
      )
    } yield
      (id1, id2, aggregates._1, aggregates._2)
  }

  protected def extractFeatureMatrixAggregates(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    groupSize: Option[Int],
    aggOut: Seq[Option[Double]] => Option[Double],
    aggIn: Seq[Option[Double]] => Option[Double],
    valueTransform: Option[Double => Double] = None
  ): Future[(Option[Double], Option[Double])] =
    if (featureNames2.nonEmpty) {
      for {
        matrix <- groupSize.map { groupSize =>
          extractFeatureMatrixGrouped(featureNames1, featureNames2, featureMatrixDsa, groupSize, valueTransform)
        }.getOrElse(
          extractFeatureMatrix(featureNames1, featureNames2, featureMatrixDsa, valueTransform)
        )
      } yield {
        extractRowColumnAggregates(matrix.toSeq, aggOut, aggIn)
      }
    } else
      Future((None, None))

  private def extractRowColumnAggregates(
                                          matrix: Seq[Seq[Option[Double]]],
                                          aggOut: Seq[Option[Double]] => Option[Double],
                                          aggIn: Seq[Option[Double]] => Option[Double]
                                        ): (Option[Double], Option[Double]) = {
    def calcAux(m: Seq[Seq[Option[Double]]]) =
      aggOut(m.par.map(aggIn).toList)

    (calcAux(matrix), calcAux(matrix.transpose))
  }

  private def extractFeatureMatrixGrouped(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    groupSize: Int,
    valueTransform: Option[Double => Double] = None
  ): Future[Traversable[Seq[Option[Double]]]] =
    for {
      matrix <-
      seqFutures(featureNames1.grouped(groupSize)) { feat1 =>
        seqFutures(featureNames2.grouped(groupSize)) { feat2 =>
          extractFeatureMatrix(feat1, feat2, featureMatrixDsa, valueTransform)
        }.map { partialColumnValues =>
          val partialColumnValuesSeq = partialColumnValues.map(_.toSeq)
          val rowNum = partialColumnValuesSeq.head.size

          for (rowIndex <- 0 to rowNum - 1) yield partialColumnValuesSeq.flatMap(_(rowIndex))
        }
      }.map(_.flatten)
    } yield
      matrix

  private def extractFeatureMatrix(
    featureNames1: Seq[String],
    featureNames2: Seq[String],
    featureMatrixDsa: DataSetAccessor,
    valueTransform: Option[Double => Double] = None
  ): Future[Traversable[Seq[Option[Double]]]] =
    for {
      jsons <- featureMatrixDsa.dataSetRepo.find(
        criteria = Seq(featureFieldName #-> featureNames1.map(_.replaceAllLiterally("u002e", "."))),
        projection = featureNames2 :+ featureFieldName
      )
    } yield {
      assert(
        jsons.size.equals(featureNames1.size),
        s"The number of extracted feature rows ${jsons.size} doesn't equal the number of features ${featureNames1.size}: ${featureNames1.mkString(",")}."
      )

      jsons.map { json =>
        featureNames2.map { featureName2 =>
          (json \ featureName2).asOpt[Double].map { value =>
            valueTransform match {
              case Some(valueTransform) => valueTransform(value)
              case None => value
            }
          }
        }
      }
    }
}
package runnables.db

import java.{util => ju}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.google.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.runnables.FutureRunnable
import org.incal.core.dataaccess.AscSort
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.JsObject
import reactivemongo.play.json.BSONObjectIDFormat
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestElasticJsonRepoStreaming @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val idName = JsObjectIdentity.name

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  private val creationDateSumSink = Sink.fold[Long, JsObject](0) { case (sum, json) =>
    val timeMs = (json \ "createdt").asOpt[Long].getOrElse(0l)
    sum + timeMs
  }

  def calcCreationDateSum(withProjection: Boolean): Future[Long] =
    for {
      biosampleTestDsa <- dsaf.getOrError("lux_park.ibbl_biosample_tests")

      biosampleTestDataSetRepo = biosampleTestDsa.dataSetRepo

      source <- biosampleTestDataSetRepo.findAsStream(
        sort = Seq(AscSort("createdt")),
        projection = if (withProjection) Seq("createdt") else Nil
      )

      // materialize the flow, getting the Sinks materialized value
      result <- source.runWith(creationDateSumSink)
    } yield
      result

  def calcCreationDateSumOld(withProjection: Boolean): Future[Long] =
    for {
      biosampleTestDsa <- dsaf.getOrError("lux_park.ibbl_biosample_tests")

      biosampleTestDataSetRepo = biosampleTestDsa.dataSetRepo

      jsons <- biosampleTestDataSetRepo.find(
        sort = Seq(AscSort("createdt")),
        projection = if (withProjection) Seq("createdt") else Nil
      )
    } yield
      jsons.map(json =>
        (json \ "createdt").asOpt[Long].getOrElse(0l)
      ).sum

  case class PersonNumeratorAccum(sum1: Double, sum2: Double, productSum: Double, count: Int)

  private val twoFeaturePearsonNumeratorSink = Sink.fold[PersonNumeratorAccum, JsObject](PersonNumeratorAccum(0, 0, 0, 0)) {
    case (PersonNumeratorAccum(sum1, sum2, productSum, count), json) =>
      val feature1 = (json \ "Feature1").as[Double]
      val feature2 = (json \ "Feature2").as[Double]

      PersonNumeratorAccum(sum1 + feature1, sum2 + feature2, productSum + feature1 * feature2, count + 1)
  }

  def calcTwoFeaturePearsonNumerator(withProjection: Boolean): Future[Double] =
    for {
      mPowerFeatureSetDsa <- dsaf.getOrError("mpower_challenge.9638887")

      mPowerFeatureSetRepo = mPowerFeatureSetDsa.dataSetRepo

      source <- mPowerFeatureSetRepo.findAsStream(
        sort = Seq(AscSort("Feature1")),
        projection = if (withProjection) Seq("Feature1", "Feature2") else Nil
      )

      // materialize the flow, getting the Sinks materialized value
      result <-
        source.runWith(twoFeaturePearsonNumeratorSink).map { case PersonNumeratorAccum(sum1, sum2, productSum, count) =>
          val mean1 = sum1 / count
          val mean2 = sum2 / count
          val pMean = productSum / count

          pMean - mean1 * mean2
        }
    } yield
      result

  def calcTwoFeaturePearsonNumeratorOld(withProjection: Boolean): Future[Double] =
    for {
      mPowerFeatureSetDsa <- dsaf.getOrError("mpower_challenge.9638887")

      mPowerFeatureSetRepo = mPowerFeatureSetDsa.dataSetRepo

      jsons <- mPowerFeatureSetRepo.find(
        sort = Seq(AscSort("Feature1")),
        projection = if (withProjection) Seq("Feature1", "Feature2") else Nil
      )
    } yield {
      val els = jsons.map { json =>
        val feature1 = (json \ "Feature1").as[Double]
        val feature2 = (json \ "Feature2").as[Double]
        (feature1, feature2)
      }

      val length = els.size

      val mean1 = els.map(_._1).sum / length
      val mean2 = els.map(_._2).sum / length

      // sum up the products
      val pMean = els.foldLeft(0.0) { case (accum, pair) => accum + pair._1 * pair._2 } / length

      pMean - mean1 * mean2
    }

  def runAsFuture =
    for {
      creationDateSum <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcCreationDateSum(false) }.map { results =>
          println(s"Creation date sum using STREAMS finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      creationDateSumOld <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcCreationDateSumOld(false) }.map { results =>
          println(s"Creation date sum using FULL data load finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      twoFeaturePearsonNumerator <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcTwoFeaturePearsonNumerator(false) }.map { results =>
          println(s"Calculation of two feature pearson numerator using STREAMS finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      twoFeaturePearsonNumeratorOld <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcTwoFeaturePearsonNumeratorOld(false) }.map { results =>
          println(s"Calculation of two feature pearson numerator using FULL data load finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      creationDateSum2 <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcCreationDateSum(true) }.map { results =>
          println(s"Creation date sum using STREAMS with PROJECTION finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      creationDateSumOld2 <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcCreationDateSumOld(true) }.map { results =>
          println(s"Creation date sum using FULL data load with PROJECTION finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      twoFeaturePearsonNumerator2 <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcTwoFeaturePearsonNumerator(true) }.map { results =>
          println(s"Calculation of two feature pearson numerator using STREAMS with PROJECTION finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }

      twoFeaturePearsonNumeratorOld2 <- {
        val calcStart = new ju.Date
        seqFutures(0 to 10) { _ => calcTwoFeaturePearsonNumeratorOld(true) }.map { results =>
          println(s"Calculation of two feature pearson numerator using FULL data load with PROJECTION finished in ${new ju.Date().getTime - calcStart.getTime} ms.")
          results.head
        }
      }
    } yield {
      println("creationDateSumOld " + creationDateSumOld)
      println("creationDateSum    " + creationDateSum)

      println
      println("twoFeaturePearsonNumeratorOld " + twoFeaturePearsonNumeratorOld)
      println("twoFeaturePearsonNumerator    " + twoFeaturePearsonNumerator)

      println
      println("creationDateSumOld2 " + creationDateSumOld2)
      println("creationDateSum2    " + creationDateSum2)

      println
      println("twoFeaturePearsonNumeratorOld2 " + twoFeaturePearsonNumeratorOld2)
      println("twoFeaturePearsonNumerator2    " + twoFeaturePearsonNumerator2)
    }
}

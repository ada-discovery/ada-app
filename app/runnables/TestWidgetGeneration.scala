package runnables
import javax.inject.Inject

import dataaccess.RepoTypes.JsonReadonlyRepo
import models.{DataView, Field, WidgetGenerationMethod}
import org.incal.core.FutureRunnable
import persistence.dataset.{DataSetAccessor, DataSetAccessorFactory}
import services.WidgetGenerationService
import org.incal.core.util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestWidgetGeneration @Inject()(
    dsaf: DataSetAccessorFactory,
    wgs: WidgetGenerationService
  ) extends FutureRunnable {

  private val repetitions = 10

//  private val dataSetIds = Seq("lux_park.clinical", "lux_park.ibbl_biosamples", "lux_park.ibbl_biosample_tests_2", "lux_park.ibbl_biosample_tests", "ml.adult") // "ml.wine_quality",

  private val dataSetIds = Seq("lux_park.ibbl_biosamples", "lux_park.ibbl_biosample_tests_2", "lux_park.ibbl_biosample_tests") // "ml.wine_quality",

  private val methods = WidgetGenerationMethod.values.toSeq.sortBy(_.toString)

  override def runAsFuture =
    for {
      _ <- seqFutures(dataSetIds)(genForDataSet)
    } yield
      ()

  def genForDataSet(dataSetId: String): Future[Unit] = {
    val dsa = dsaf(dataSetId).get
    val dataSetRepo = dsa.dataSetRepo

    for {
      name <- dsa.dataSetName
      setting <- dsa.setting
      views <- dsa.dataViewRepo.find()
      fields <- dsa.fieldRepo.find()

      // warm-up
      _ <- dataSetRepo.find()

      viewMethodTimes <-
        seqFutures( for { view <- views; method <- methods } yield (view, method) ) { case (view, method) =>
          genWidgets(dataSetRepo, fields, view, method).map ( time =>
            (view, method, time)
          )
        }
    } yield
      viewMethodTimes.groupBy(_._1).foreach { case (view, items) =>
        println(s"$name, ${setting.storageType}, ${view.name}")
        println("----------------------")
        items.map { case (_, method, time) =>
          println(s"${method.toString}: $time")
        }
        println
      }
  }

  private def genWidgets(
    dataSetRepo: JsonReadonlyRepo,
    fields: Traversable[Field],
    view: DataView,
    method: WidgetGenerationMethod.Value
  ): Future[Long] = {
    val start = new java.util.Date()
    for {
      widgets <- seqFutures((1 to repetitions)) { _ =>
        wgs.apply(view.widgetSpecs, dataSetRepo, Nil, Map(), fields, method)
      }
    } yield
      (new java.util.Date().getTime - start.getTime) / repetitions
  }
}
package runnables

import com.google.inject.Inject
import org.ada.server.models.dataimport.{CsvDataSetImport, RedCapDataSetImport}
import org.incal.core.runnables.FutureRunnable
import org.ada.server.dataaccess.RepoTypes._
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.GuiceRunnableApp

import scala.concurrent.ExecutionContext.Implicits.global

class TestDataSetImportRepo @Inject() (repo: DataSetImportRepo) extends FutureRunnable {

  override def runAsFuture =
    for {
      _ <- repo.save(CsvDataSetImport(None, "My Study", "my_study.lala1", "Lala 1", Some("/home/temp/data1.csv"), ",", None, None, true, false))
      _ <- repo.save(CsvDataSetImport(None, "My Study", "my_study.lala2", "Lala 2", Some("/home/temp/data2.csv"), ";", None, None, true, false))
      _ <- repo.save(RedCapDataSetImport(None, "My Study", "my_study.lala3", "Lala 3","https:/something.com/rest", "54j39gryu65936f3", false))

      csvSearchResult <- repo.find(
        criteria = Seq("dataSetId" #== "my_study.lala1"),
        projection = Seq("concreteClass", "dataSpaceName", "dataSetName", "dataSetId", "delimiter", "timeCreated")
      )

      redCapSearchResult <- repo.find(
        criteria = Seq("dataSetId" #== "my_study.lala3"),
        projection = Seq("concreteClass", "dataSpaceName", "dataSetName", "dataSetId", "url", "token", "timeCreated")
      )

      all <- repo.find()
    } yield {
      val lala1 = csvSearchResult.head.asInstanceOf[CsvDataSetImport]
      println("Lala1")
      println(s"${lala1.dataSpaceName}, ${lala1.dataSetName}, ${lala1.dataSetId}, ${lala1.delimiter}, ${lala1.timeCreated}")

      val lala3 = redCapSearchResult.head.asInstanceOf[RedCapDataSetImport]
      println("Lala3")
      println(s"${lala3.dataSpaceName}, ${lala3.dataSetName}, ${lala3.dataSetId}, ${lala3.url}, ${lala3.token}, ${lala1.timeCreated}")

      println("All:")
      println(all.map(importInfo => s"${importInfo._id.get.stringify}, ${importInfo.dataSpaceName}, ${importInfo.dataSetName}, ${importInfo.dataSetId}").mkString("\n"))
    }
}

object TestDataSetImportRepo extends GuiceRunnableApp[TestDataSetImportRepo]

package runnables.db

import java.util.Date
import javax.inject.Inject

import dataaccess.FieldRepoFactory
import org.incal.play.GuiceRunnableApp

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class TestIgniteDictionaryFindAll @Inject() (dictionaryFieldRepoFactory: FieldRepoFactory) extends Runnable {

  private val dataSetId = "denopa.raw_clinical_baseline"
  private val fieldRepo = dictionaryFieldRepoFactory(dataSetId)

  override def run = {
    val future =
      for {
        _ <- testFindAll()
        _ <- testFindAll()
        _ <- testFindAll()
        t1 <- testFindAll(Seq("name", "fieldType"))
        t2 <- testFindAll(Seq("name", "fieldType"))
        t3 <- testFindAll(Seq("name", "fieldType"))
        t4 <- testFindAll(Seq("name", "fieldType"))
        t5 <- testFindAll(Seq("name", "fieldType"))
        t6 <- testFindAll(Seq("name", "fieldType"))
        t7 <- testFindAll(Seq("name", "fieldType"))
        t8 <- testFindAll(Seq("name", "fieldType"))
        t9 <- testFindAll(Seq("name", "fieldType"))
        t10 <- testFindAll(Seq("name", "fieldType"))
      } yield
        println("Total projection time: " + Seq(t1, t2, t3, t3, t5, t6, t7, t8, t9, t10).sum.toDouble / 10)

    Await.result(future, 2 minutes)
  }

  private def testFindAll(projection: Traversable[String] = Nil): Future[Long] =
    for {
      duration <- {
        val startTime = new Date()
        fieldRepo.find(projection = projection).map(_ =>
          new Date().getTime - startTime.getTime
        )
      }
    } yield {
      println(duration)
      duration
    }
}

object TestIgniteDictionaryFindAll extends GuiceRunnableApp[TestIgniteDictionaryFindAll]

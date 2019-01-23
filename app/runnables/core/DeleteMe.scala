package runnables.core

import javax.inject.Inject

import models.DataSetFormattersAndIds.CategoryIdentity
import org.incal.core.dataaccess.Criterion._
import org.incal.core.dataaccess.EqualsNullCriterion
import org.incal.core.{FutureRunnable, InputFutureRunnable}
import persistence.dataset.DataSetAccessorFactory
import reactivemongo.bson.BSONObjectID

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DeleteMe @Inject()(dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dataSetId = "trend.clinical_visit"
  private val refCategoryIds = Seq(
    BSONObjectID.parse("5b87e51be40000eb01a3bea5").get,
    BSONObjectID.parse("5b87e51be40000f801a3beb2").get,
    BSONObjectID.parse("5b87e51be40000f801a3bec0").get
  )

  override def runAsFuture: Future[Unit] = {
    val dsa = dsaf(dataSetId).get

    for {
      count <- dsa.categoryRepo.count()
      top3 <- dsa.categoryRepo.find(limit = Some(3))
      top3Ids = top3.map(_._id.get).toSeq
      found1 <- dsa.categoryRepo.find(Seq(CategoryIdentity.name #== Some(top3Ids.head)))
      found2 <- dsa.categoryRepo.get(top3Ids.head)
      found3 <- dsa.categoryRepo.find(Seq(CategoryIdentity.name #-> top3Ids.map(Some(_))))
      found4 <- dsa.categoryRepo.find(Seq(CategoryIdentity.name #-> refCategoryIds.map(Some(_))))
      found5 <- dsa.categoryRepo.find(Seq("parentId" #== None))
      found6 <- dsa.categoryRepo.find(Seq(EqualsNullCriterion("parentId")))
      found7 <- dsa.categoryRepo.find(Seq("name" #== "labor"))
    } yield {
      println(s"Total count $count.")
      println("1: " + found1.map(category => s"${category._id}, ${category.name}"))
      println("2: " + found2.map(category => s"${category._id}, ${category.name}"))
      println("3: " + found3.map(category => s"${category._id}, ${category.name}"))
      println("4: " + found4.map(category => s"${category._id}, ${category.name}"))
      println("5: " + found5.map(category => s"${category._id}, ${category.name}"))
      println("6: " + found6.map(category => s"${category._id}, ${category.name}"))
      println("7: " + found7.map(category => s"${category._id}, ${category.name}"))
    }
  }
}

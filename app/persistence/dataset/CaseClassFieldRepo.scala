package persistence.dataset

import dataaccess._
import dataaccess.Criterion._
import dataaccess.RepoTypes.FieldRepo
import models.ml.RCPredictionSettingAndResults
import models.{Field, FieldTypeId}
import util.FieldUtil.caseClassToFlatFieldTypes
import scala.concurrent.ExecutionContext.Implicits.global

import scala.reflect.runtime.universe._
import scala.concurrent.Await
import scala.concurrent.duration._

object CaseClassFieldRepo {
  def apply[T: TypeTag]: FieldRepo = {
    val fieldTypes = caseClassToFlatFieldTypes[T]("-").filter(_._1 != "_id")
    val fields = fieldTypes.map { case (name, spec) =>
      val enumValues = spec.enumValues.map(_.map { case (a, b) => (a.toString, b)})
      Field(name, Some(util.toHumanReadableCamel(name)), spec.fieldType, spec.isArray, enumValues)
    }.toSeq

    TransientLocalFieldRepo(fields)
  }
}

object TestCaseClassFieldRepo extends App {

  private val fieldRepo = CaseClassFieldRepo[RCPredictionSettingAndResults]

  val future = for {
    all <- fieldRepo.find()
    doubleFields <- fieldRepo.find(
      criteria = Seq("fieldType" #-> Seq(FieldTypeId.Double, FieldTypeId.Integer)),
      sort = Seq(AscSort("name"))
    )
    rnmseField2 <- fieldRepo.get("meanRnmseLast")
    count <- fieldRepo.count()
    fields10Sorted <- fieldRepo.find(skip = Some(2), limit = Some(10), sort = Seq(AscSort("fieldType"), AscSort("name")))
  } yield {
    println(all.mkString("\n"))
    println
    println(count)
    println
    println(doubleFields.mkString("\n"))
    println
    println(rnmseField2)
    println
    println(fields10Sorted.mkString("\n"))
  }

  Await.result(future, 1 minute)
}
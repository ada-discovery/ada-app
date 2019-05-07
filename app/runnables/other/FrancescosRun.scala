package runnables.other

import javax.inject.Inject
import org.ada.server.dataaccess.JsonReadonlyRepoExtra._
import org.ada.server.dataaccess.RepoTypes.UserRepo
import org.ada.server.models.FieldTypeId
import org.incal.core.runnables.InputFutureRunnable
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.incal.core.dataaccess.EqualsCriterion
import play.api.{Configuration, Logger}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe.typeOf

class FrancescosRun @Inject() (dsaf: DataSetAccessorFactory, configuration: Configuration, userRepo: UserRepo) extends InputFutureRunnable[FrancescosRunSpec] {

  private val logger = Logger

  override def runAsFuture(input: FrancescosRunSpec) = {
    val dsa = dsaf(input.dataSetId).getOrElse(
      throw new IllegalArgumentException(s"Data set ${input.dataSetId} not found")
    )

    val projectName = configuration.getString("project.name").getOrElse("N/A")

    for {
      // total count
      count <- dsa.dataSetRepo.count()

      // total field count
      fieldCount <- dsa.fieldRepo.count()

      // retrieve all the double fields
      doubleFields <- dsa.fieldRepo.find(Seq("fieldType" #== FieldTypeId.Double))

      // get the min value of a given field
      minValue <- dsa.dataSetRepo.min(input.fieldName, addNotNullCriterion = true)

      // get the max value of a given field
      maxValue <- dsa.dataSetRepo.max(input.fieldName, addNotNullCriterion = true)

      user1 <- userRepo.find(Seq(EqualsCriterion("ldapDn", input.email))).map(_.headOption)
      user <- userRepo.find(Seq("ldapDn" #== input.email)).map(_.headOption)
    } yield {

      logger.info(s"Item/field count: $count/$fieldCount")

      val doubleFieldNamesString = doubleFields.map(_.name).mkString(", ")
      logger.info(s"Double fields: $doubleFieldNamesString")

      logger.info(s"Min/max value of ${input.fieldName}: $minValue/$maxValue.")

      logger.info("User's name (if found): " + user.map(_.ldapDn).getOrElse("N/A"))
    }
  }

  override def inputType = typeOf[FrancescosRunSpec]
}

case class FrancescosRunSpec(
  dataSetId: String,
  fieldName: String,
  email: String
)
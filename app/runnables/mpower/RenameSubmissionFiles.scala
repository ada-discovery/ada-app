package runnables.mpower

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import play.api.libs.json._
import org.incal.core.InputFutureRunnable
import org.incal.core.util.listFiles
import services.DataSetService
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files.copy
import java.nio.file.Paths.get
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class RenameSubmissionFiles @Inject()(
    dsaf: DataSetAccessorFactory,
    dataSetService: DataSetService
  ) extends InputFutureRunnable[RenameSubmissionFilesSpec] {

  private val submissionIdFieldName = "submissionId"
  private val submissionNameFieldName = "submissionName"
  private val fieldNames = Seq(submissionIdFieldName, submissionNameFieldName)

  implicit def toPath(filename: String) = get(filename)

  override def runAsFuture(spec: RenameSubmissionFilesSpec) = {
    val dsa = dsaf(spec.scoreBoardDataSetId).get

    for {
      jsons <- dsa.dataSetRepo.find(projection = fieldNames)
    } yield {
      val submissionFileNameIdMap = jsons.flatMap { json =>
        val submissionId = (json \ submissionIdFieldName).toOption.map { submissionIdJsValue =>
          submissionIdJsValue.asOpt[String].getOrElse(submissionIdJsValue.as[Int].toString)
        }
        (json \ submissionNameFieldName).asOpt[String].map { submissionName =>
          (submissionName.replace(' ', '_'), submissionId.get)
        }
      }.toMap

      listFiles(spec.inputFolderName).map { submissionFile =>
        submissionFileNameIdMap.get(submissionFile.getName) match {
          case Some(submissionId) => {
            println(s"Copying ${submissionFile.getName} to a new location.")
            copy(submissionFile.getAbsolutePath, spec.outputFolderName + "/" + submissionId + ".csv", REPLACE_EXISTING)
          }
          case None => println(s"Submission file ${submissionFile.getName} has NO id!!!")
        }
      }
    }
  }

  override def inputType = typeOf[RenameSubmissionFilesSpec]
}

case class RenameSubmissionFilesSpec(
  scoreBoardDataSetId: String,
  inputFolderName: String,
  outputFolderName: String
)
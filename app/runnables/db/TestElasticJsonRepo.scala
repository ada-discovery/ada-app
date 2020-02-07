package runnables.db

import com.google.inject.Inject
import org.ada.server.models.DataSetFormattersAndIds.JsObjectIdentity
import org.incal.core.runnables.FutureRunnable
import org.incal.core.dataaccess.AscSort
import org.incal.core.dataaccess.Criterion.Infix
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONObjectIDFormat

import scala.concurrent.ExecutionContext.Implicits.global

class TestElasticJsonRepo @Inject() (dsaf: DataSetAccessorFactory) extends FutureRunnable {

  private val dsa = dsaf("lux_park.ibbl_biosamples").get
  private val dataSetRepo = dsa.dataSetRepo
  private val idName = JsObjectIdentity.name

  def runAsFuture =
    for {
      idsAndSampleTypeIds <- dataSetRepo.find(projection = Seq("__id.$oid", "sampletypeid"))

      firstTwo <- dataSetRepo.find(limit = Some(2))
      firstTwoIds = firstTwo.map(jsObject => (jsObject \ idName).as[BSONObjectID])

      sampleTypeIdResult <- dataSetRepo.find(Seq("sampletypeid" #== 8))

      _ <- dataSetRepo.find(
        criteria = Seq("sampletypeid" #== 8)
      )

      sampleTypeIdProjectionResult <- dataSetRepo.find(Seq("sampletypeid" #== 8))

      _ <- dataSetRepo.find(
        criteria = Seq("sampletypeid" #== 8),
        projection = Seq("samplefamilyid")
      )

      _ <- dataSetRepo.find(
        criteria = Seq(idName #>= firstTwoIds.head),
        limit = Some(10),
        sort = Seq(AscSort(idName)),
        projection = Seq(idName, "samplefamilyid")
      )

      kitCreationDateResult <- dataSetRepo.find(
        criteria = Seq(
          "kitcreationdate" #> new java.util.Date(1425395566000l),
          "kitcreationdate" #< new java.util.Date(1425595566000l)
        ),
        sort = Seq(AscSort("sampletypeid"))
      )

      kitCreationDateProjectionResult <- dataSetRepo.find(
        criteria = Seq(
          "kitcreationdate" #> new java.util.Date(1425395566000l),
          "kitcreationdate" #< new java.util.Date(1425595566000l)
        ),
        sort = Seq(AscSort("sampletypeid")),
        projection = Seq("kitcreationdate", idName)
      )

      sampleFamilyIdInResult <- dataSetRepo.find(
        criteria = Seq(
          "samplefamilyid" #-> Seq("SF-150303-00433", "SF-150303-00506")
        ),
        sort = Seq(AscSort(idName))
      )

      sampleFamilyIdInProjectionResult <- dataSetRepo.find(
        criteria = Seq(
          "samplefamilyid" #-> Seq("SF-150303-00433", "SF-150303-00506")
        ),
        sort = Seq(AscSort(idName)),
        projection = Seq("samplefamilyid", idName)
      )

      containerTypeIdNotInResult <- dataSetRepo.find(
        criteria = Seq(
          "containertypeid" #!-> Seq(7, 8)
        ),
        sort = Seq(AscSort("containertypeid"))
      )

      containerTypeIdNotInProjectionResult <- dataSetRepo.find(
        criteria = Seq(
          "containertypeid" #!-> Seq(7, 8)
        ),
        sort = Seq(AscSort("containertypeid")),
        projection = Seq("kitcreationdate")
      )

      idResult <- dataSetRepo.find(
        criteria = Seq(
          idName #== firstTwoIds.head
        ),
        sort = Seq(AscSort("kitcreationdate"))
      )

      idProjectionResult <- dataSetRepo.find(
        criteria = Seq(
          idName #== firstTwoIds.head
        ),
        sort = Seq(AscSort("kitcreationdate")),
        projection = Seq("samplefamilyid", idName)
      )

      idInResult <- dataSetRepo.find(
        criteria = Seq(
          idName #-> firstTwoIds.toSeq
        ),
        sort = Seq(AscSort("kitcreationdate"))
      )

      idInProjectionResult <- dataSetRepo.find(
        criteria = Seq(
          idName #-> firstTwoIds.toSeq
        ),
        sort = Seq(AscSort("kitcreationdate")),
        projection = Seq("samplefamilyid", idName)
      )

      updateId <- {
        val updatedJson = firstTwo.head.+(("containertypeid", Json.toJson(123)))
        dataSetRepo.update(updatedJson)
      }

      updatedResult <- dataSetRepo.get(updateId)
    } yield {
      println("sampleTypeIdResult: " + sampleTypeIdResult.size)
      println(checkIfIdContained(sampleTypeIdResult))

      println("sampleTypeIdProjectionResult: " + sampleTypeIdProjectionResult.size)
      println(checkIfIdContained(sampleTypeIdProjectionResult))

      println("kitCreationDateResult: " + kitCreationDateResult.size)
      println(checkIfIdContained(kitCreationDateResult))

      println("kitCreationDateProjectionResult: " + kitCreationDateProjectionResult.size)
      println(checkIfIdContained(kitCreationDateProjectionResult))

      println("sampleFamilyIdInResult: " + sampleFamilyIdInResult.size)
      println(checkIfIdContained(sampleFamilyIdInResult))

      println("sampleFamilyIdInProjectionResult: " + sampleFamilyIdInProjectionResult.size)
      println(checkIfIdContained(sampleFamilyIdInProjectionResult))

      println("containerTypeIdNotInResult: " + containerTypeIdNotInResult.size)
      println(checkIfIdContained(containerTypeIdNotInResult))

      println("containerTypeIdNotInProjectionResult: " + containerTypeIdNotInProjectionResult.size)
      println(checkIfIdContained(containerTypeIdNotInProjectionResult))

      println("idResult: " + idResult.size)
      println(checkIfIdContained(idResult))

      println("idProjectionResult: " + idProjectionResult.size)
      println(checkIfIdContained(idProjectionResult))

      println("idInResult: " + idInResult.size)
      println(checkIfIdContained(idInResult))

      println("idInProjectionResult: " + idInProjectionResult.size)
      println(checkIfIdContained(idInProjectionResult))

      println("updatedResult containertypeid: " + (updatedResult.get \ "containertypeid").as[Int])
      println(checkIfIdContained(Seq(updatedResult.get)))
    }

  private def checkIfIdContained(jsons: Traversable[JsObject]): Boolean =
    jsons.forall(json => (json \ JsObjectIdentity.name).toOption.isDefined)
}

//object TestElasticJsonRepo extends GuiceRunnableApp[TestElasticJsonRepo] with App { run }
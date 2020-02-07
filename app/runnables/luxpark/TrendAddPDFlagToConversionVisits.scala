package runnables.luxpark

import akka.stream.Materializer
import org.incal.core.dataaccess.StreamSpec
import javax.inject.Inject
import org.ada.server.models.{Field, FieldTypeId}
import org.incal.core.dataaccess.{Criterion, NotEqualsNullCriterion}
import org.incal.core.dataaccess.Criterion._
import org.incal.core.runnables.{FutureRunnable, InputFutureRunnable, InputFutureRunnableExt}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import play.api.libs.json.{JsBoolean, Json}
import org.ada.server.services.DataSetService
import org.ada.server.field.FieldUtil._
import org.ada.server.models.datatrans.ResultDataSetSpec

import scala.concurrent.ExecutionContext.Implicits.global

class TrendAddPDFlagToConversionVisits @Inject()(
    dsaf: DataSetAccessorFactory,
    dss: DataSetService)(
    implicit materializer: Materializer
  ) extends InputFutureRunnableExt[TrendAddPDFlagToConversionVisitsSpec] {

  private val dataSetId = "trend.clinical_visit"
  private val conversionVisitFieldName  = "konversion_pd_erhebung"
  private val visitFieldName = "redcap_event_name"
  private val idFieldName = "id"
  private val basisVisitName = "basis"

  private val convertedToPDFieldName = "derived-converted_to_pd"
  private val isPDFieldName = "derived-is_pd"


  private val orderedVisits = Seq("basis", "bl", "fu1", "fu2", "fu3", "fu4")
  private val visitIndexMap = orderedVisits.zipWithIndex.toMap

  override def runAsFuture(input: TrendAddPDFlagToConversionVisitsSpec) = {
    val dsa = dsaf(dataSetId).get

    for {
      conversionField <- dsa.fieldRepo.get(conversionVisitFieldName).map(_.get)

      visitField <- dsa.fieldRepo.get(visitFieldName).map(_.get)

      conversionNamedType = conversionField.toNamedTypeAny
      visitNamedType = visitField.toNamedTypeAny
      basisVisitValue = visitNamedType._2.displayStringToValue(basisVisitName).get

      fields <- dsa.fieldRepo.find()
      newFields = fields ++ Seq(
        Field(convertedToPDFieldName, Some("Converted to PD"), FieldTypeId.Boolean),
        Field(isPDFieldName, Some("PD Group"), FieldTypeId.Boolean)
      )

      idConversionJsons <- dsa.dataSetRepo.find(
        criteria = Seq(NotEqualsNullCriterion(conversionVisitFieldName), visitFieldName #== basisVisitValue),
        projection = Seq(idFieldName, conversionVisitFieldName)
      )

      idConversionVisitMap = idConversionJsons.map { json =>
        val id = (json \ idFieldName).as[Int]
        val conversionVisit = json.toDisplayString(conversionNamedType).toLowerCase
        (id, conversionVisit)
      }.toMap

      inputStream <- dsa.dataSetRepo.findAsStream()

      alteredStream = inputStream.map { json =>
        val id = (json \ idFieldName).as[Int]
        val visit = json.toDisplayString(visitNamedType).toLowerCase

        val (convertedToPD, isPD) = idConversionVisitMap.get(id).map { conversionVisit =>
          val conversionVisitIndex = visitIndexMap.get(conversionVisit).get
          val currentVisitIndex = visitIndexMap.get(visit).get
          (conversionVisitIndex == currentVisitIndex, conversionVisitIndex <= currentVisitIndex)
        }.getOrElse((false, false))

        json.++(Json.obj(
          convertedToPDFieldName -> JsBoolean(convertedToPD),
          isPDFieldName -> JsBoolean(isPD)
        ))
      }

      _ <- dss.saveDerivedDataSet(dsa, input.resultDataSetSpec, alteredStream, newFields.toSeq, input.streamSpec, true)
    } yield
      ()
  }
}

case class TrendAddPDFlagToConversionVisitsSpec(
  resultDataSetSpec: ResultDataSetSpec,
  streamSpec: StreamSpec
)
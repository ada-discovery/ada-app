package runnables

import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

import dataaccess.ConversionUtil
import persistence.dataset.DataSetAccessorFactory

import scala.concurrent.Future
import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global

class CheckStringToDateConversion @Inject() (dsaf: DataSetAccessorFactory) extends InputFutureRunnable[CheckStringToDateConversionSpec] {

  private val format = "yyyy-MM-dd HH:mm:ss.SS"
  private val converter = ConversionUtil.toDate(Seq(format))

  override def runAsFuture(
    input: CheckStringToDateConversionSpec
  ): Future[Unit] = {
    val dsa = dsaf(input.dataSetId).get
    for {
      values <- dsa.dataSetRepo.find(projection = Seq(input.fieldName)).map(
        _.flatMap(json =>
          (json \ input.fieldName).asOpt[String]
        )
      )
    } yield
      values.foreach { value =>
        converter(value)
      }
  }

  override def inputType = typeOf[CheckStringToDateConversionSpec]
}

case class CheckStringToDateConversionSpec(
  dataSetId: String,
  fieldName: String
)
package unit

import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder

import scala.io.Codec

class CsvImporterTest extends FlatSpec with GuiceOneAppPerSuite {

  private implicit val codec = Codec.UTF8
  private val irisCsv = getClass.getResource("/iris.csv").toString

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .build()

  "CsvDataSetImport" should "run without error" in {
    val importInfo = CsvDataSetImport(
      dataSpaceName = "",
      dataSetId = "iris",
      dataSetName = "iris",
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = false,
      path = Some(irisCsv)
    )
    val importer = app.injector.instanceOf[DataSetCentralImporter]
    importer(importInfo)
  }

}

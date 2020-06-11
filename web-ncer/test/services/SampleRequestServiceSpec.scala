package services

import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.{DataSetSetting, StorageType}
import org.ada.server.models.dataimport.CsvDataSetImport
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest.{AsyncFlatSpec, BeforeAndAfter}
import scala.concurrent.duration._

import scala.concurrent.Await

class SampleRequestServiceSpec extends AsyncFlatSpec with BeforeAndAfter {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val sampleRequestService = InjectorWrapper.instanceOf[SampleRequestService]
  private val dataSetImporter = InjectorWrapper.instanceOf[DataSetCentralImporter]
  private val dsaf = InjectorWrapper.instanceOf[DataSetAccessorFactory]

  private object Iris {
    val path = getClass.getResource("/iris.csv").getPath
    val id = "test.iris"
    val name = "iris"
    val rowNum = 150
    val colNum = 5
    def importInfo(storageType: StorageType.Value) = CsvDataSetImport(
      dataSpaceName = "test",
      dataSetName = name,
      dataSetId = id,
      delimiter = ",",
      matchQuotes = false,
      inferFieldTypes = true,
      path = Some(path),
      setting = Some(new DataSetSetting(id, storageType))
    )
  }

  before {
    Await.result(
      dataSetImporter(Iris.importInfo(StorageType.ElasticSearch)),
      10 seconds
    )
  }

  after {
    dsaf(Iris.id) map { _.dataSetRepo.deleteAll }
  }

  behavior of "createCsv"

  it should "return a string representing a CSV file" in {
    for {
      csv <- sampleRequestService.createCsv(Iris.id)
    } yield {
      val rows = csv.split("\n")
      assert(rows.length == Iris.rowNum + 1)
      assert(rows.forall(_.split("\t").length == Iris.colNum))
    }
  }

  behavior of "getCatalogueItems"

  it should "return a list of catalogue items" in {
    for {
      items <- sampleRequestService.getCatalogueItems
    } yield assert(items.nonEmpty)
  }

}

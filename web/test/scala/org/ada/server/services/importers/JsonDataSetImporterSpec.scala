package scala.org.ada.server.services.importers

import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.models.dataimport.JsonDataSetImport
import org.ada.server.models.{DataSetSetting, StorageType}
import org.ada.server.services.ServiceTypes.DataSetCentralImporter
import org.scalatest._

import scala.org.ada.server.services.InjectorWrapper

class JsonDataSetImporterSpec extends AsyncFlatSpec with BeforeAndAfter {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val importer = InjectorWrapper.instanceOf[DataSetCentralImporter]
  private val dsaf = InjectorWrapper.instanceOf[DataSetAccessorFactory]

  private object Iris {
    val path = getClass.getResource("/iris.json").getPath
    val id = "test.iris"
    val name = "iris"
    val size = 150
    val fieldNum = 5
    def importInfo(storageType: StorageType.Value) = JsonDataSetImport(
      dataSpaceName = "test",
      dataSetName = name,
      dataSetId = id,
      inferFieldTypes = true,
      path = Some(path),
      setting = Some(new DataSetSetting(id, storageType))
    )
  }

  after {
    for {
      dsa <- dsaf.getOrError(Iris.id)
      _ <- dsa.dataSetRepo.deleteAll
    } yield
      ()
  }

  behavior of "JsonDataSetImporter"

  it should "import iris.json to MongoDB" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.Mongo))
      dsa <- dsaf(Iris.id).map(_.getOrElse(fail(s"Dataset '${Iris.name}' not found in Mongo.")))
      _ <- dsa.dataSetRepo.flushOps
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetRepo.count() map { count => assert(count == Iris.size)}
      _ <- dsa.fieldRepo.count() map { count => assert(count == Iris.fieldNum)}
    } yield succeed
  }

  it should "import iris.json to ElasticSearch" in {
    for {
      _ <- importer(Iris.importInfo(StorageType.ElasticSearch))
      dsa <- dsaf(Iris.id).map(_.getOrElse(fail(s"Dataset '${Iris.name}' not found in Mongo.")))
      _ <- dsa.dataSetRepo.flushOps
      _ <- dsa.dataSetName map { name => assert(name == Iris.name) }
      _ <- dsa.dataSetRepo.count() map { count => assert(count == Iris.size)}
      _ <- dsa.fieldRepo.count() map { count => assert(count == Iris.fieldNum)}
    } yield succeed
  }
}

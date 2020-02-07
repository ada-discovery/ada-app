package services

import java.io.FileOutputStream
import java.nio.file.Paths

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import org.ada.server.AdaException
import org.apache.commons.io.IOUtils
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy
import org.irods.jargon.core.connection.{ClientServerNegotiationPolicy, IRODSAccount, IRODSSession, IRODSSimpleProtocolManager}
import org.irods.jargon.core.pub.IRODSAccessObjectFactoryImpl
import org.irods.jargon.core.pub.domain.User
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry
import play.api.Configuration

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Random

@ImplementedBy(classOf[IRodsServiceImpl])
trait IRodsService {
  def listUsers: Traversable[User]

  def listFolderEntries(path: String): Traversable[CollectionAndDataObjectListingEntry]

  def getFile(path: String): Source[ByteString, Future[IOResult]]
}

@Singleton
class IRodsServiceImpl @Inject() (configuration:  Configuration) extends IRodsService {

  private val irodsConnectionManager = new IRODSSimpleProtocolManager
  irodsConnectionManager.initialize()
  private val irodsSession = new IRODSSession(irodsConnectionManager)
  private val irodsAccessObjectFactory = new IRODSAccessObjectFactoryImpl(irodsSession)
  private val irodsAccount = createAccount

  private val userAO = irodsAccessObjectFactory.getUserAO(irodsAccount)
  private val collectionAndDataObjectAO = irodsAccessObjectFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount)
  private val fileSystemAO = irodsAccessObjectFactory.getIRODSFileFactory(irodsAccount)

  private val tempFolder = System.getProperty("java.io.tmpdir")

  // creates an account using the properties from the conf file
  private def createAccount: IRODSAccount = {
    val host = getConfMust("irods.host") // "lcsb-cdc-irods-test.lcsb.uni.lu"
    val port = configuration.getInt("irods.port").getOrElse(1247)
    val userName = getConfMust("irods.username") // syscidada"
    val password = getConfMust("irods.password") //"9UlqI0zN2e28"
    val homeDirectory = getConfMust("irods.home_dir") // "/lcsbZone/home/syscidada"
    val zone = getConfMust("irods.zone") // "lcsbZone"
    val defaultStorageResource = "test1-resc"
    val clientServerNegotiationPolicy = new ClientServerNegotiationPolicy()
    clientServerNegotiationPolicy.setSslNegotiationPolicy(SslNegotiationPolicy.NO_NEGOTIATION)

    IRODSAccount.instance(host, port, userName, password, homeDirectory, zone, defaultStorageResource, clientServerNegotiationPolicy)
  }

  private def getConfMust(path: String) = configuration.getString(path).getOrElse(
    throw new AdaException(s"IRODS service requires '$path' to be set.")
  )

  override def listUsers: Traversable[User] =
    userAO.findAll.toSeq

  override def listFolderEntries(path: String) =
    collectionAndDataObjectAO.listDataObjectsAndCollectionsUnderPath(path).toSeq // "/lcsbZone/home/syscidada/testdata"

  def getFile(path: String): Source[ByteString, Future[IOResult]] = {
    val fileInputStream = fileSystemAO.instanceIRODSFileInputStream(path)

    val tempFileName = tempFolder + "/irods_temp_" + Random.nextInt(1000000) + 100000

    val target = new FileOutputStream(tempFileName)

    try {
      IOUtils.copy(fileInputStream, target)
    } finally {
      fileInputStream.close()
      target.close
    }

    val tempFilePath = Paths.get(tempFileName)

    FileIO.fromPath(tempFilePath)
  }
}
package runnables.core

import java.io.{FileOutputStream, InputStream}

import org.incal.core.util.writeByteStream
import org.ada.web.runnables.RunnableFileOutput
import org.apache.commons.io.IOUtils
import org.irods.jargon.core.connection.ClientServerNegotiationPolicy.SslNegotiationPolicy
import org.irods.jargon.core.connection.{ClientServerNegotiationPolicy, IRODSAccount, IRODSSession, IRODSSimpleProtocolManager}
import org.irods.jargon.core.pub.IRODSAccessObjectFactoryImpl
import akka.stream.scaladsl.{FileIO, Source, StreamConverters}
import java.nio.file.StandardOpenOption._
import java.nio.file.{Path, Paths}

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.ByteString
import org.incal.core.runnables.FutureRunnable

import scala.collection.JavaConversions._
import scala.concurrent.Future
import scala.util.Random

class iRodsTest extends FutureRunnable with RunnableFileOutput {



  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def runAsFuture = Future {

    //    //  val file = collectionAndDataObjectAO.getCollectionAndDataObjectListingEntryAtGivenAbsolutePath("/lcsbZone/home/syscidada/testdata/10_Red.idat")
    //

//    val fileInputStream = fileSystemAO.instanceIRODSFileInputStream("/lcsbZone/home/syscidada/testdata/10_Red.idat")
//
//
//    val tempFileName = tempFolder + "/irods_temp_" + Random.nextInt(100000) + 100000
//
//    val target = new FileOutputStream(tempFileName)
//
//    try {
//      IOUtils.copy(fileInputStream, target)
//    } finally {
//      fileInputStream.close()
//      target.close
//    }
//
//    val tempFilePath = Paths.get(tempFileName)
//
//    val bs = FileIO.fromPath(tempFilePath)
//
////    val iterator = scala.io.Source.fromInputStream(fileInputStream).getLines().map(ByteString(_))
////    val bs = Source.fromIterator(() => iterator)
////
//    setOutputByteSource(bs)
//
//    setOutputFileName("10_Red.idat")

//    java.nio.file.Files.delete(tempFilePath)

//    Future {
//      //      val bs = StreamConverters.fromInputStream(() => fileInputStream)
//      (fileInputStream, bs)
//    }.flatMap { case (fileInputStream, byteSource) =>
//      val fileOutputSink = FileIO.toPath(new java.io.File("/home/peter/Downloads/10_Red.idat").toPath, options = Set(CREATE, WRITE, TRUNCATE_EXISTING))
//
//      byteSource.runWith(fileOutputSink).andThen {
//        case _ => fileInputStream.close()
//      }.map(_ => ())
//    }

    //    writeByteStream(byteSource, new java.io.File("/home/peter/Downloads/10_Red.idat"))




  }
}
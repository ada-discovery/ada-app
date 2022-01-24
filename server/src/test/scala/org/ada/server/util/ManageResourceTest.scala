package org.ada.server.util

import org.ada.server.{AdaException, AdaParseException}
import org.ada.server.util.ManageResource._
import org.scalatest._

import java.io.{FileNotFoundException, IOException}
import java.nio.charset.UnsupportedCharsetException
import scala.io.{BufferedSource, Source}

class ManageResourceTest extends FlatSpec with Matchers {

  def getResource: BufferedSource = Source.fromFile(getClass.getResource("/dummyResource.txt").getPath)

  def getResourceErrPath: BufferedSource = Source.fromFile(getClass.getResource("/").getPath)

  def getResourceUnsupCharsetExc: BufferedSource = Source.fromFile(getClass.getResource("/dummyResource.txt").getPath, "NotExistEncoding")

  it should " throw AdaException 'using Fn' if a null resource is passed" in {
    a [AdaException] should be thrownBy {
      using(null) (_ => println("Error test resource"))
    }
  }

  it should "print lines and close resource" in {
    val bufferedResource = getResource
    using(bufferedResource){ source => {
        for(line <- source.getLines())
          println(line)
    }}

    a [IOException] should be thrownBy(bufferedResource.getLines())
  }

  it should " throw AdaException 'closeResource Fn' if a null resource is passed" in {
    a [AdaException] should be thrownBy {
      closeResource(null)
    }
  }

  it should "close the resource" in {
    val bufferedResource = getResource
    closeResource(bufferedResource)
    a [IOException] should be thrownBy(bufferedResource.length)
  }

  it should " throw AdaParserException and close resource" in {
    val bufferedResource = getResource
    a [AdaParseException] should be thrownBy {
       using(bufferedResource){source => {
         for(line <- source.getLines()){
           println(line)
           throw new AdaParseException("Error Parsing Test")
         }
       }}
    }

    a [IOException] should be thrownBy(bufferedResource.length)
  }

  it should "throw FileNotFoundException" in {
    a [FileNotFoundException] should be thrownBy{
      using(getResourceErrPath){_ => println("Error")}
    }
  }

  it should "throw UnsupportedCharsetException" in {
    a [UnsupportedCharsetException] should be thrownBy{
      using(getResourceUnsupCharsetExc){ _ => println("Error")}
    }
  }


}

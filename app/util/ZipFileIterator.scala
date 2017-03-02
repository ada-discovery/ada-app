package util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.ZipInputStream

import org.apache.commons.io.IOUtils

object ZipFileIterator {

  protected class ZipStreamIterator(zin: ZipInputStream) extends Iterator[(String, String)] {

    private var ze = zin.getNextEntry

    override def hasNext() = {
      val isNext = ze != null
      if (!isNext)
        zin.close
      isNext
    }

    override def next() = {
      val baos = new ByteArrayOutputStream(2048)
      IOUtils.copy(zin, baos)
      val string = baos.toString("UTF-8")
      baos.close
      zin.closeEntry
      val result = (ze.getName, string)
      ze = zin.getNextEntry
      result
    }
  }

  def apply(bytes: Array[Byte]): Iterator[(String, String)] =
    new ZipStreamIterator(
      new ZipInputStream(
        new ByteArrayInputStream(bytes)))
}

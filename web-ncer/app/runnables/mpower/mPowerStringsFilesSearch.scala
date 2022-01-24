package runnables.mpower

import java.io.File
import scala.io.Source
import org.ada.server.util.ManageResource.using

object mPowerStringsFilesSearch extends App {

  private val rootFolder = "/home/peter/Projects/mPower-2-iOS"
  private val extension = "strings"

  val foundFiles = searchRecursively(new File(rootFolder))

  val fileCounts = foundFiles.map(getCounts)

  fileCounts.foreach((report(_,_,_)).tupled)

  val totalLineCount = fileCounts.map(_._2).sum
  val totalWordCount = fileCounts.map(_._3).sum

  println
  println("Total entry count: " + totalLineCount)
  println("Total word count: " + totalWordCount)

  private def searchRecursively(folder: File): Seq[File] = {
    val files = folder.listFiles

    val foundFiles = files.filter(file => file.isFile && file.getName.endsWith("." + extension)).toSeq
    val nestedFoundFiles = files.filter(_.isDirectory).flatMap(searchRecursively)

    foundFiles ++ nestedFoundFiles
  }

  private def getCounts(file: File): (File, Int, Int) = {
    using(Source.fromFile(file)){
      source => {
        val lines = source.getLines().toSeq
        val linesWithEntries = lines.filter(_.contains(" = "))

        val linesWithEntriesCount = linesWithEntries.size

        val wordCounts = linesWithEntries.map { line =>
          val entryWithQuotes = line.split(" = ")(1).split(";")(0)
          val entry = entryWithQuotes.substring(1, entryWithQuotes.length - 1)

          entry.count(_.isSpaceChar) + 1
        }

        val totalWordCount = wordCounts.sum

        (file, linesWithEntriesCount, totalWordCount)
      }
    }
  }

  private def report(file: File, entryCount: Int, wordCount: Int) =
    println(file.getPath + " -> " + entryCount + " (entries), " + wordCount + " (words)")
}

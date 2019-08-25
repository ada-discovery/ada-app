package services.request

import java.io.File

import models.NotificationInfo
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import play.api.libs.json.{JsObject, JsValue}

class PdfBuilder {

  def getFile(notificationInfo: NotificationInfo): File = {

    val document =  new PDDocument()
    val page = new PDPage
    val tempFile = File.createTempFile("temp-ada-email-attachment", ".tmp")

    document.addPage(page)
    buildContent(document, page, notificationInfo)
    document.save(tempFile)
    document.close()

    tempFile
  }

  def addText(text: String, content: PDPageContentStream)={
    content.showText(text)
  }

  def buildLines(text: String, content: PDPageContentStream)= {
    // TODO: having "var" is bad... use zipWithIndex
    var offsetY = 700
 //   content.newLineAtOffset(25, offsetY)

    text.split("\n").foreach { line =>
      content.beginText()
      offsetY = offsetY - 15
      content.newLineAtOffset(25, offsetY)
      content.setFont(PDType1Font.TIMES_ROMAN, 9)
      content.showText(line)
      content.endText()
    }
  }

  def buildContent(document: PDDocument, page: PDPage, notification: NotificationInfo)={
    val content = new PDPageContentStream(document, page)

    val text = "Request resume\n" + MessageTemplate.format(
      notification.notificationType ,
      notification.targetUser,
      notification.userRole.toString,
      notification.createdByUser,
      notification.dataSetId,
      notification.creationDate,
      notification.fromState,
      notification.toState,
      notification.updateDate,
      notification.updatedByUser,
      notification.getRequestUrl) + "\nDescription:\n \n"+notification.description + "\n \n"

    val itemsToPrint = notification.items.get.page.items.map(i=>i.fields.filter(f=>f._1 != "_id"))
    // TODO: long line; introduce intermediates
    val table = "table\n \n" + itemsToPrint.toSeq(0).map(item => item._1).mkString(" ") + "\n"  + itemsToPrint.map(item => "-> " + buildListLine(item)).mkString("\n")

    buildLines(text + table, content)
    content.close()
  }

  // TODO: remove this function
  def buildListLine(item: Seq[(String, JsValue)])=
    item.map(_._2.toString()).mkString(" ")
}
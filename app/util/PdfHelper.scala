package util

import java.io.File

import models.NotificationInfo
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}
import play.api.libs.json.JsValue
import services.request.MessageTemplate

object PdfHelper {

    def getFile(notificationInfo: NotificationInfo): File = {

        val document = new PDDocument()
        val page = new PDPage
        val tempFile = File.createTempFile("temp-ada-email-attachment", ".tmp")

        document.addPage(page)
        buildContent(document, page, notificationInfo)
        document.save(tempFile)
        document.close()

        tempFile
    }

    private def buildContent(document: PDDocument, page: PDPage, notification: NotificationInfo) = {
        val content = new PDPageContentStream(document, page)

        val text = "Request resume\n" + MessageTemplate.format(
            notification.notificationType,
            notification.targetUser,
            notification.userRole.toString,
            notification.createdByUser,
            notification.dataSetId,
            notification.creationDate,
            notification.fromState,
            notification.toState,
            notification.updateDate,
            notification.updatedByUser,
            notification.getRequestUrl
        ) + "\nDescription:\n \n" + notification.description.getOrElse("") + "\n \n"

        val itemsToPrint = notification.items.get.map(_.fields)
        val table = buildTable(itemsToPrint)

        buildLines(text + table, content)
        content.close()
    }

    private def buildLines(text: String, content: PDPageContentStream) = {
        val offsetY = 700
        text.split("\n").zipWithIndex.map { case (line, index) =>
            content.beginText()
            content.newLineAtOffset(25, offsetY - (15 * index))
            content.setFont(PDType1Font.TIMES_ROMAN, 9)
            content.showText(line)
            content.endText()
        }
    }

    private def buildTable(itemsToPrint: Traversable[Seq[(String, JsValue)]]) = {
        val header = itemsToPrint.toSeq(0).map(item => item._1).mkString(" ") + "\n"
        val rows = itemsToPrint.map(item => "-> " + item.map(_._2.toString()).mkString(" ")).mkString("\n")

        "table\n \n" + header + rows
    }

    private def addText(text: String, content: PDPageContentStream) = {
        content.showText(text)
    }
}
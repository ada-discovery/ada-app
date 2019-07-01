package services.request

import java.io.File

import models.NotificationInfo
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.{PDDocument, PDPage, PDPageContentStream}

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

  def buildLines(text: String, content :PDPageContentStream)={


  var offsetY = 700
 //   content.newLineAtOffset(25, offsetY)


    text.split("\n").foreach(line=>{
      content.beginText()
      offsetY = offsetY-15
      content.newLineAtOffset(25, offsetY)
      content.setFont(PDType1Font.TIMES_ROMAN, 9)
      content.showText(line)
      content.endText()
    })

  }


  def buildContent(document: PDDocument, page: PDPage, notification: NotificationInfo)={
    val content = new PDPageContentStream(document, page)


val text = "Request resume\n" + MessageTemplate.format(
  notification.notificationType ,
  notification.targetUser,
  notification.userRole.toString,
  notification.requestId,
  notification.createdByUser,
  notification.creationDate,
  notification.fromState,
  notification.toState,
  notification.updateDate,
  notification.updatedByUser,
  notification.getRequestUrl) + "\nDescription:\n"+notification.description

    buildLines(text, content)






    content.close()
  }

}

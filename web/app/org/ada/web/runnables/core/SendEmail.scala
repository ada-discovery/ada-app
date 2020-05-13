package org.ada.web.runnables.core

import javax.inject.Inject
import org.ada.server.AdaException
import org.ada.web.runnables.InputView
import org.incal.core.runnables.{InputRunnable, InputRunnableExt}
import org.incal.play.controllers.WebContext
import play.api.Configuration
import play.api.libs.mailer.{Email, MailerClient}
import views.html.elements._
import org.incal.play.controllers.WebContext._
import org.incal.core.util.toHumanReadableCamel

class SendEmail @Inject()(mailerClient: MailerClient, configuration: Configuration) extends InputRunnableExt[SendEmailSpec] with InputView[SendEmailSpec] {

  override def run(input: SendEmailSpec) = {

    if (configuration.getString("play.mailer.host").isEmpty) {
      throw new AdaException("Email cannot be sent. The configuration entry 'play.mailer.host' is not set.")
    }

    val email = Email(
      from = input.from,
      to = Seq(input.to),
      subject = input.subject,
      bodyText = Some(input.body)
    )

    mailerClient.send(email)
  }

  override def inputFields(
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) =  (form) => {
    def inputTextAux(fieldName: String) =
      inputText("sendEmail", fieldNamePrefix.getOrElse("") + fieldName, form, Seq('_label -> toHumanReadableCamel(fieldName)))

    html(
      inputTextAux("from"),
      inputTextAux("to"),
      inputTextAux("subject"),
      textarea("sendEmail", fieldNamePrefix.getOrElse("") + "body", form, Seq('cols -> 60, 'rows -> 20, '_label -> "Body"))
    )
  }
}

case class SendEmailSpec(
  from: String,
  to: String,
  subject: String,
  body: String
)
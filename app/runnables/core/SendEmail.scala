package runnables.core

import javax.inject.Inject
import org.incal.core.runnables.{InputRunnable, RunnableHtmlOutput}
import play.api.libs.mailer.{Email, MailerClient}

import scala.reflect.runtime.universe.typeOf

class SendEmail @Inject()(mailerClient: MailerClient) extends InputRunnable[SendEmailSpec] {

  override def run(input: SendEmailSpec) = {

    val email = Email(
      from = input.from,
      to = Seq(input.to),
      subject = input.subject,
      bodyText = Some(input.body)
    )

    mailerClient.send(email)
  }

  override def inputType = typeOf[SendEmailSpec]
}

case class SendEmailSpec(
  from: String,
  to: String,
  subject: String,
  body: String
)
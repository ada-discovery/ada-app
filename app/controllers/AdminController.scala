package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import controllers.core.{GenericMapping, WebContext}
import persistence.RepoTypes.MessageRepo
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller, Request}
import play.api.Play.current
import play.api.data.Form
import runnables.InputRunnable
import util.MessageLogger
import util.ReflectionUtil.findClasses
import util.SecurityUtil.restrictAdmin
import views.html.{admin => adminviews}
import java.{util => ju}

class AdminController @Inject() (
    deadbolt: DeadboltActions,
    messageRepo: MessageRepo,
    messagesApi: MessagesApi
  ) extends Controller {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  // we scan only the jars starting with this prefix to speed up the class search
  private val libPrefix = "ncer-pd"

  private implicit def webContext(implicit request: Request[_]) = WebContext(messagesApi)

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def listRunnables = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val classes1 = findClasses[Runnable](libPrefix, Some("runnables."), None)
      val classes2 = findClasses[InputRunnable[_]](libPrefix, Some("runnables."), None)

      val runnableNames = (classes1 ++ classes2).map(_.getName).sorted
      Ok(adminviews.runnables(runnableNames))
    }
  }

  private val runnablesRedirect = Redirect(routes.AdminController.listRunnables())

  def runScript(className: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      try {
        val clazz = Class.forName(className, true, this.getClass.getClassLoader)
        val instance = current.injector.instanceOf(clazz)

        if (instance.isInstanceOf[InputRunnable[_]]) {
           val inputRunnable = instance.asInstanceOf[InputRunnable[_]]
 //          val fields = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.typ)
           val mapping = GenericMapping[Any](inputRunnable.inputType)
           Ok(adminviews.formFieldsInput(
             className.split('.').last, Form(mapping), routes.AdminController.runInputScript(className)
           ))
        } else {
          val start = new ju.Date()
          instance.asInstanceOf[Runnable].run()
          val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
          val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."

          messageLogger.info(message)
          runnablesRedirect.flashing("success" -> message)
        }
      } catch {
        case e: ClassNotFoundException => {
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")
        }
        case e: Exception => {
          logger.error(s"Script ${className} failed", e)
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
        }
      }
    }
  }

  def runInputScript(className: String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      try {
        println(className)
        val clazz = Class.forName(className, true, this.getClass.getClassLoader)
        val start = new ju.Date()
        val inputRunnable = current.injector.instanceOf(clazz).asInstanceOf[InputRunnable[Any]]
        val mapping = GenericMapping[Any](inputRunnable.inputType)

        Form(mapping).bindFromRequest().fold(
          { formWithErrors =>
            BadRequest(adminviews.formFieldsInput(
              className.split('.').last, formWithErrors, routes.AdminController.runInputScript(className)
            ))
          },
          input => {
            inputRunnable.run(input)
            val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
            val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."
            messageLogger.info(message)
            runnablesRedirect.flashing("success" -> message)
          }
        )
      } catch {
        case e: ClassNotFoundException =>
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")

        case e: Exception =>
          logger.error(s"Script ${className} failed", e)
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
      }
    }
  }
}
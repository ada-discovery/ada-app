package controllers

import javax.inject.Inject

import be.objectify.deadbolt.scala.DeadboltActions
import persistence.RepoTypes.MessageRepo
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import util.MessageLogger
import util.ReflectionUtil._
import util.SecurityUtil.restrictAdmin

class AdminController @Inject() (deadbolt: DeadboltActions, messageRepo: MessageRepo) extends Controller {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  @Inject var messagesApi: MessagesApi = _

  // we scan only the jars starting with this prefix to speed up the class search
  val libPrefix = "ncer-pd"

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def listRunnables = restrictAdmin(deadbolt) {
    Action { implicit request =>
      val classes = findClasses[Runnable](libPrefix, Some("runnables."), None)
      val runnableNames = classes.map(_.getName).sorted

      implicit val msg = messagesApi.preferred(request)
      Ok(views.html.admin.runnables(runnableNames))
    }
  }

  private val runnablesRedirect = Redirect(routes.AdminController.listRunnables())

  /**
    * Runs the script given its path (i.e. "runnables.denopa.DeNoPaCleanup").
    *
    * @param className Path of runnable to launch.
    * @return Redirects to listRunnables()
    */
  def runScript(className : String) = restrictAdmin(deadbolt) {
    Action { implicit request =>
      implicit val msg = messagesApi.preferred(request)
      try {
        val clazz = Class.forName(className)
        val runnable = current.injector.instanceOf(clazz).asInstanceOf[Runnable]
        val start = new java.util.Date()
        runnable.run()
        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script ${className} was successfully executed in ${execTimeSec} sec."
        messageLogger.info(message)
        runnablesRedirect.flashing("success" -> message)
      } catch {
        case e: ClassNotFoundException => {
          runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")
        }
        case e: Exception => {
          runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
        }
      }
    }
  }
}
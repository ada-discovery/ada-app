package controllers

import java.io.File
import javax.inject.Inject

import org.clapper.classutil.ClassFinder
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, Controller}
import play.api.Play.current

class AdminController extends Controller {

  @Inject var messagesApi: MessagesApi = _

  /**
    * Creates view showign all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def listRunnables = Action { implicit request =>
    val classpath = List(".").map(new File(_))
    val finder = ClassFinder(classpath)
    val runnableClassInfos = finder.getClasses.filter{ classInfo =>
      try {
        classInfo.name.startsWith("runnables") &&
          classInfo.isConcrete &&
            classOf[Runnable].isAssignableFrom(Class.forName(classInfo.name))
      } catch {
        case _ : ClassNotFoundException => false
        case _ : ExceptionInInitializerError => false
        case _ : NoClassDefFoundError => false
      }
    }
    val runnableNames = runnableClassInfos.map(_.name).sorted

    implicit val msg = messagesApi.preferred(request)
    Ok(views.html.admin.runnables(runnableNames))
  }

  private val runnablesRedirect = Redirect(routes.AdminController.listRunnables())

  /**
    * Runs the script given its path (i.e. "runnables.denopa.DeNoPaCleanup").
    *
    * @param className Path of runnable to launch.
    * @return Redirects to listRunnables()
    */
  def runScript(className : String) = Action { implicit request =>
    implicit val msg = messagesApi.preferred(request)
    try {
      val clazz = Class.forName(className)
      val runnable = current.injector.instanceOf(clazz).asInstanceOf[Runnable]
      val start = new java.util.Date()
      runnable.run()
      val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
      runnablesRedirect.flashing("success" -> s"Script ${className} successfully executed in ${execTimeSec} sec.")
    } catch {
      case _ : ClassNotFoundException => runnablesRedirect.flashing("errors" -> s"Script ${className} does not exist.")
      case e : Exception => runnablesRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
    }
  }
}
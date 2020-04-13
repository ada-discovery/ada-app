package org.ada.web.controllers

import java.util.Date

import javax.inject.Inject
import org.ada.web.controllers.core.{AdaBaseController, AdaCrudControllerImpl, GenericMapping}
import org.ada.server.dataaccess.RepoTypes.{MessageRepo, RunnableSpecRepo}
import org.ada.server.util.MessageLogger
import org.ada.server.util.ClassFinderUtil.findClasses
import org.incal.play.controllers.{AdminRestrictedCrudController, BaseController, HasBasicFormCreateView, HasBasicFormCrudViews, HasBasicFormEditView, HasBasicListView, HasFormShowEqualEditView, WebContext, WithNoCaching}
import play.api.{Configuration, Logger}
import play.api.data.Form
import play.api.mvc.{AnyContent, Request, Result, WrappedRequest}
import views.html.{runnable => runnableViews}
import java.{util => ju}

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.AdaException
import org.ada.server.field.FieldUtil
import org.ada.server.models.ScheduledTime.fillZeroes
import org.ada.server.models.dataimport.DataSetImport
import org.ada.server.models.{DataSetSetting, DataSpaceMetaInfo, RunnableSpec, ScheduledTime, Translation, User}
import org.ada.server.services.ServiceTypes.RunnableScheduler
import org.ada.web.runnables.{InputView, RunnableFileOutput}
import org.ada.web.util.WebExportUtil
import org.incal.core.util.{retry, toHumanReadableCamel}
import org.incal.core.runnables._
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import org.incal.play.security.{AuthAction, SecurityRole}
import play.api.data.Forms.{date, default, ignored, mapping, optional}
import play.api.inject.Injector
import play.api.libs.json.{JsArray, Json}
import reactivemongo.bson.BSONObjectID
import runnables.DsaInputFutureRunnable

import scala.reflect.runtime.universe.{TypeTag, typeOf}
import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.data.Forms._

class RunnableController @Inject() (
  messageRepo: MessageRepo,
  runnableSpecRepo: RunnableSpecRepo,
  runnableScheduler: RunnableScheduler,
  configuration: Configuration,
  injector: Injector
) extends AdaCrudControllerImpl[RunnableSpec, BSONObjectID](runnableSpecRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[RunnableSpec, BSONObjectID]
  with HasFormShowEqualEditView[RunnableSpec, BSONObjectID]
  with MappingHelper {

  private val logger = Logger
  private val messageLogger = MessageLogger(logger, messageRepo)

  // we scan only the jars starting with this prefix to speed up the class search
  private val basePackages = Seq(
    "org.ada.server.runnables",
    "org.ada.server.runnables.core",
    "org.ada.web.runnables",
    "org.ada.web.runnables.core",
    "runnables",
    "runnables.core"
  )

  private val packages = basePackages ++ configuration.getStringSeq("runnables.extra_packages").getOrElse(Nil)
  private val searchRunnableSubpackages = configuration.getBoolean("runnables.subpackages.enabled").getOrElse(false)

  private val runnablesHomeRedirect = Redirect(routes.RunnableController.selectRunnable())
  private val appHomeRedirect = Redirect(routes.AppController.index())

  protected[controllers] lazy val form: Form[RunnableSpec] = Form(
    mapping(
      "_id" -> ignored(Option.empty[BSONObjectID]),
      "input" -> ignored(Map.empty[String, String]),
      "runnableClassName" -> nonEmptyText,
      "name" -> nonEmptyText,
      "scheduled" -> boolean,
      "scheduledTime" -> optional(scheduledTimeMapping),
      "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
      "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
    )(RunnableSpec.apply)(RunnableSpec.unapply).verifying(
      "Runnable is marked as 'scheduled' but no time provided",
      importInfo => (!importInfo.scheduled) || (importInfo.scheduledTime.isDefined)
    )
  )

  override protected val homeCall = routes.RunnableController.find()

  // Create

  private val noRunnableClassNameRedirect = goHome.flashing("errors" -> "No runnable class name specified.")

  override def create = AuthAction { implicit request =>
    getRunnableClassName(request).map( dataSetId =>
      if (dataSetId.trim.nonEmpty)
        super.create(request)
      else
        Future(noRunnableClassNameRedirect)
    ).getOrElse(
      Future(noRunnableClassNameRedirect)
    )
  }

  override protected def createView = { implicit ctx =>
    val runnableClassName = runnableClassNameOrError

    val instance = getInjectedInstance(runnableClassName)
    val inputs = htmlInputs(instance)

    runnableViews.create(_, runnableClassName, inputs)
  }

  // Edit

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      val runnableClassName = data.form("runnableClassName").value.getOrElse(
        runnableClassNameOrError
      )
      val instance = getInjectedInstance(runnableClassName)
      val inputs = htmlInputs(instance)

      runnableViews.edit(data, runnableClassName, inputs)
  }

  // Save / Form

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[RunnableSpec] = {
    implicit val authRequest = request.asInstanceOf[AuthenticatedRequest[AnyContent]]
    val runnableClassName = runnableClassNameOrError

    val instance = getInjectedInstance(runnableClassName)

    instance match {
      case inputRunnable: InputRunnable[_] =>
        val (inputForm, _) = genericInputFormAndFields(inputRunnable)

      case _ =>
    }

    println(request.body)

    form.bindFromRequest
  }

  // List

  override protected def listView = { implicit ctx =>
    (runnableViews.list(_, _)).tupled
  }

  // Save

  override protected def saveCall(
    runnableSpec: RunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedScheduledTime = runnableSpec.copy(
      scheduledTime =  runnableSpec.scheduledTime.map(fillZeroes)
    )

    super.saveCall(specWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, specWithFixedScheduledTime); id
    }
  }

  // Update

  override protected def updateCall(
    runnableSpec: RunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedScheduledTime = runnableSpec.copy(
      scheduledTime =  runnableSpec.scheduledTime.map(fillZeroes)
    )

    super.updateCall(specWithFixedScheduledTime).map { id =>
      scheduleOrCancel(id, specWithFixedScheduledTime); id
    }
  }

  /**
    * Creates view showing all runnables.
    * The view provides an option to launch the runnables and displays feedback once the job is finished.
    *
    * @return View listing all runnables in directory "runnables".
    */
  def selectRunnable = restrictAdminAny(noCaching = true) { implicit request =>
    Future {
      Ok(runnableViews.runnableSelection())
    }
  }

  private def findRunnableNames: Seq[String] = {
    def findAux[T](implicit m: ClassTag[T]) =
      packages.map { packageName =>
        findClasses[T](Some(packageName), !searchRunnableSubpackages)
      }.foldLeft(Stream[Class[T]]()) {
        _ ++ _
      }

    val foundClasses =
      findAux[Runnable] ++
      findAux[InputRunnable[_]] ++
      findAux[InputRunnableExt[_]] ++
      findAux[FutureRunnable] ++
      findAux[InputFutureRunnable[_]] ++
      findAux[InputFutureRunnableExt[_]] ++
      findAux[DsaInputFutureRunnable[_]]

    foundClasses.map(_.getName).sorted
  }

  def runScript(className: String) = scriptActionAux(className) { implicit request =>
    instance =>
      val start = new ju.Date()

      if (instance.isInstanceOf[Runnable]) {
        // plain runnable - execute immediately

        val runnable = instance.asInstanceOf[Runnable]
        runnable.run()

        val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
        val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

        handleRunnableOutput(runnable, message)
      } else {
        // input or input-output runnable needs a form to be filled by a user
        Redirect(routes.RunnableController.getScriptInputForm(className))
      }
  }

  def execute(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Runnable #${id.stringify} not found"))
      ) { runnableSpec =>
        Future {
          val start = new Date()
          val instance = getInjectedInstance(runnableSpec.runnableClassName)

          instance match {
            case runnable: Runnable => runnable.run()

            case _ => ???
          }

          val execTimeSec = (new Date().getTime - start.getTime) / 1000

          render {
            case Accepts.Html() => referrerOrHome().flashing("success" -> s"Runnable '${runnableSpec.runnableClassName}' has been executed in $execTimeSec sec(s).")
            case Accepts.Json() => Created(Json.obj("message" -> s"Runnable executed in $execTimeSec sec(s)", "name" -> runnableSpec.runnableClassName))
          }
        }.recover(handleExceptions("execute"))
      })
   }

  def getScriptInputForm(className: String) = scriptActionAux(className) { implicit request =>
    instance =>

      instance match {
        // input runnable
        case inputRunnable: InputRunnable[_] =>
          val (_, inputFields) = genericInputFormAndFields(inputRunnable)
          Ok(runnableViews.runnableInput(
            className.split('.').last, routes.RunnableController.runInputScript(className), inputFields
          ))

        // plain runnable - no form
        case _ =>
          runnablesHomeRedirect.flashing("errors" -> s"No form available for the script/runnable ${className}.")
    }
  }

  def runInputScript(className: String) = scriptActionAux(className) { implicit request =>
    instance =>
      val start = new ju.Date()

      val inputRunnable = instance.asInstanceOf[InputRunnable[Any]]
      val (form, inputFields) = genericInputFormAndFields(inputRunnable)

      form.bindFromRequest().fold(
        { formWithErrors =>
          BadRequest(runnableViews.runnableInput(
            instance.getClass.getSimpleName, routes.RunnableController.runInputScript(className), inputFields, formWithErrors.errors
          ))
        },
        input => {
          inputRunnable.run(input)

          val execTimeSec = (new java.util.Date().getTime - start.getTime) / 1000
          val message = s"Script '${toShortHumanReadable(className)}' was successfully executed in ${execTimeSec} sec."

          handleRunnableOutput(inputRunnable, message)
        }
      )
  }

  private def genericInputFormAndFields[T](
    inputRunnable: InputRunnable[T])(
    implicit webContext: WebContext
  ) =
    inputRunnable match {
      // input runnable with a custom fields view
      case inputView: InputView[T] =>

        val form = genericForm(inputRunnable)
        val inputFieldsView = inputView.inputFields(implicitly[WebContext])(form.asInstanceOf[Form[T]])

        (form, inputFieldsView)

      // input runnable with a generic fields view
      case _ =>

        val form = genericForm(inputRunnable)

        val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).toMap
        val inputFieldsView = runnableViews.genericFields(form, nameFieldTypeMap)

        (form, inputFieldsView)
    }

  private def genericForm(inputRunnable: InputRunnable[_]): Form[Any] = {
    val mapping = GenericMapping[Any](inputRunnable.inputType)
    Form(mapping)
  }

  private def scriptActionAux(
    className: String)(
    action: AuthenticatedRequest[AnyContent] => Any => Result
  ) = WithNoCaching {
    restrictAdminOrPermissionAny(s"RN:$className") {
      implicit request =>
        for {
          user <- currentUser()
        } yield {
          val isAdmin = user.map(_.isAdmin).getOrElse(false)
          val errorRedirect = if (isAdmin) runnablesHomeRedirect else appHomeRedirect

          try {
            val instance = getInjectedInstance(className)
            action(request)(instance)
          } catch {
            case _: ClassNotFoundException =>
              errorRedirect.flashing("errors" -> s"Script ${className} does not exist.")

            case e: Exception =>
              logger.error(s"Script ${className} failed", e)
              errorRedirect.flashing("errors" -> s"Script ${className} failed due to: ${e.getMessage}")
          }
        }
    }
  }

  private def handleRunnableOutput(
    runnable: Any,
    message: String)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    messageLogger.info(message)

    runnable match {
      // has a file output
      case fileOutput: RunnableFileOutput =>
        fileOutput.outputByteSource.map ( outputByteSource =>
          WebExportUtil.streamToFile(outputByteSource, fileOutput.fileName)
        ).getOrElse(
          WebExportUtil.stringToFile(fileOutput.output.toString(), fileOutput.fileName)
        )

      // has a HTML output
      case htmlOutput: RunnableHtmlOutput =>

        val output = htmlOutput.output.mkString
        Ok(runnableViews.runnableOutput(runnable.getClass, output, message))

      // no output
      case _ =>
        runnablesHomeRedirect.flashing("success" -> message)
    }
  }

  def getRunnableNames = restrictAdminAny(noCaching = true) {
    implicit request => Future {
      val runnableIdAndNames =  findRunnableNames.map { runnableName =>
        Json.obj("name" -> runnableName, "label" -> toShortHumanReadable(runnableName))
      }
      Ok(JsArray(runnableIdAndNames))
    }
  }

  // aux funs

  private def htmlInputs(instance: Any)(implicit webContext: WebContext) =
    instance match {
      // input runnable
      case inputRunnable: InputRunnable[_] =>
        val (_, inputFields) = genericInputFormAndFields(inputRunnable)
        Some(inputFields)

      // plain runnable - no form
      case _ => None
    }

  private def runnableClassNameOrError(implicit ctx: WebContext): String = {
    val runnableClassName = getRunnableClassName(ctx.request.asInstanceOf[AuthenticatedRequest[AnyContent]])

    runnableClassName.getOrElse(
      throw new AdaException("No runnableClassName specified.")
    )
  }

  private def getRunnableClassName(request: AuthenticatedRequest[AnyContent]): Option[String] =
    request.queryString.get("runnableClassName") match {
      case Some(matches) => Some(matches.head)
      case None => request.body.asFormUrlEncoded.flatMap(_.get("runnableClassName").map(_.head))
    }

  private def toShortHumanReadable(className: String) = {
    val shortName = className.split("\\.", -1).lastOption.getOrElse(className)
    toHumanReadableCamel(shortName)
  }

  private def getInjectedInstance(className: String) = {
    val clazz = Class.forName(className, true, currentThreadClassLoader)
    injector.instanceOf(clazz)
  }

  private def scheduleOrCancel(id: BSONObjectID, runnableSpec: RunnableSpec): Unit = {
    if (runnableSpec.scheduled)
      runnableScheduler.schedule(runnableSpec.scheduledTime.get)(id)
    else
      runnableScheduler.cancel(id)
  }
}
package org.ada.web.controllers

import java.util.Date

import javax.inject.Inject
import org.ada.web.controllers.core.{AdaBaseController, AdaCrudControllerImpl, GenericMapping}
import org.ada.server.dataaccess.RepoTypes.{BaseRunnableSpecRepo, MessageRepo, RunnableSpecRepo}
import org.ada.server.util.MessageLogger
import org.ada.server.util.ClassFinderUtil.findClasses
import org.incal.play.controllers.{AdminRestrictedCrudController, BaseController, HasBasicFormCreateView, HasBasicFormCrudViews, HasBasicFormEditView, HasBasicListView, HasFormShowEqualEditView, WebContext, WithNoCaching}
import play.api.{Configuration, Logger}
import play.api.data.{Form, Mapping}
import play.api.mvc.{AnyContent, Request, Result, WrappedRequest}
import views.html.{runnable => runnableViews}
import java.{util => ju}

import be.objectify.deadbolt.scala.AuthenticatedRequest
import org.ada.server.AdaException
import org.ada.server.field.FieldUtil
import org.ada.server.models.ScheduledTime.fillZeroes
import org.ada.server.models.{BaseRunnableSpec, DataSetSetting, InputRunnableSpec, RunnableSpec, Schedulable, ScheduledTime, User}
import RunnableSpec.{BaseRunnableSpecIdentity, baseFormat}
import org.ada.server.services.ServiceTypes.{RunnableExec, RunnableScheduler}
import org.ada.web.runnables.{InputView, RunnableFileOutput}
import org.ada.web.util.WebExportUtil
import org.incal.core.util.{ReflectionUtil, retry, toHumanReadableCamel}
import org.incal.core.runnables._
import org.incal.core.util.ReflectionUtil.currentThreadClassLoader
import org.incal.play.security.{AuthAction, SecurityRole}
import play.api.data.Forms.{date, default, ignored, mapping, optional}
import play.api.inject.Injector
import play.api.libs.json.{JsArray, Json}
import reactivemongo.bson.BSONObjectID
import runnables.DsaInputFutureRunnable

import scala.reflect.ClassTag
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.data.Forms._
import play.twirl.api.Html

class RunnableController @Inject() (
  messageRepo: MessageRepo,
  runnableSpecRepo: BaseRunnableSpecRepo,
  runnableExec: RunnableExec,
  runnableScheduler: RunnableScheduler,
  configuration: Configuration,
  injector: Injector
) extends AdaCrudControllerImpl[BaseRunnableSpec, BSONObjectID](runnableSpecRepo)
  with AdminRestrictedCrudController[BSONObjectID]
  with HasBasicFormCrudViews[BaseRunnableSpec, BSONObjectID]
  with HasFormShowEqualEditView[BaseRunnableSpec, BSONObjectID]
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

  private lazy val runnablesHomeRedirect = Redirect(routes.RunnableController.selectRunnable())
  private lazy val appHomeRedirect = Redirect(routes.AppController.index())

  protected[controllers] lazy val form: Form[BaseRunnableSpec] = formWithNoInput.asInstanceOf[Form[BaseRunnableSpec]]

  private val formWithNoInput: Form[RunnableSpec] = Form(
      mapping(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "runnableClassName" -> nonEmptyText,
        "name" -> nonEmptyText,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      )(RunnableSpec.apply)(RunnableSpec.unapply).verifying(
        "Runnable is marked as 'scheduled' but no time provided",
        runnableSpec => (!runnableSpec.scheduled) || (runnableSpec.scheduledTime.isDefined)
      )
    )

  private def formWithInput(inputMapping: Mapping[Any]) = {
    Form(
      mapping(
        "_id" -> ignored(Option.empty[BSONObjectID]),
        "input" -> inputMapping,
        "runnableClassName" -> nonEmptyText,
        "name" -> nonEmptyText,
        "scheduled" -> boolean,
        "scheduledTime" -> optional(scheduledTimeMapping),
        "timeCreated" -> default(date("yyyy-MM-dd HH:mm:ss"), new Date()),
        "timeLastExecuted" -> optional(date("yyyy-MM-dd HH:mm:ss"))
      )(InputRunnableSpec.apply)(InputRunnableSpec.unapply).verifying(
        "Runnable is marked as 'scheduled' but no time provided",
        runnableSpec => (!runnableSpec.scheduled) || (runnableSpec.scheduledTime.isDefined)
      )
    )
  }

  override protected val entityNameKey = "runnableSpec"
  override protected def formatId(id: BSONObjectID) = id.stringify

  override protected lazy val homeCall = routes.RunnableController.find()

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
    form: CreateViewData =>
      val runnableClassName = runnableClassNameOrError
      val inputsHtml = htmlInputsAux(runnableClassName, form)

      runnableViews.create(form, runnableClassName, inputsHtml)
  }

  // Edit

  override protected def editView = { implicit ctx =>
    data: EditViewData =>
      val runnableClassName = data.form("runnableClassName").value.getOrElse(
        runnableClassNameOrError
      )
      val inputsHtml = htmlInputsAux(runnableClassName, data.form)

      runnableViews.edit(data, runnableClassName, inputsHtml)
  }

  private def htmlInputsAux(
    runnableClassName: String,
    form: Form[BaseRunnableSpec])(
    implicit webContext: WebContext
  ): Option[Html] = {
    val runnableInstance = getInjectedInstance(runnableClassName)

    runnableInstance match {
      // input runnable
      case inputRunnable: InputRunnable[_] =>
        val inputForm = genericForm(inputRunnable, Some("input."))

        val inputFormWithData =
          if (form.hasErrors || form.value.isDefined) {
            val inputData = form.data.filter(_._1.startsWith("input."))
            inputForm.bind(inputData)
          } else {
            inputForm
          }

        val inputs = htmlInputs(inputRunnable, inputFormWithData, Some("input."))
        Some(inputs)

      // plain runnable - no form
      case _ => None
    }
  }

  // Save / Form

  override protected def formFromRequest(implicit request: Request[AnyContent]): Form[BaseRunnableSpec] = {
    implicit val authRequest = request.asInstanceOf[AuthenticatedRequest[AnyContent]]
    val runnableClassName = runnableClassNameOrError

    val instance = getInjectedInstance(runnableClassName)

    val finalForm: Form[BaseRunnableSpec] = instance match {

      // input runnable - map inputs
      case inputRunnable: InputRunnable[_] =>
        val inputForm = genericForm(inputRunnable, None)
        val inputMapping = inputForm.mapping
        formWithInput(inputMapping).asInstanceOf[Form[BaseRunnableSpec]]

      // plain runnable - no inputs
      case _ => formWithNoInput.asInstanceOf[Form[BaseRunnableSpec]]
    }

    finalForm.bindFromRequest
  }

  override def fillForm(runnableSpec: BaseRunnableSpec): Form[BaseRunnableSpec] =
    runnableSpec match {
      case x: InputRunnableSpec[_] =>
        val inputType = ReflectionUtil.classNameToRuntimeType(
          x.input.getClass.getName, ReflectionUtil.newCurrentThreadMirror
        )
        val inputMapping = GenericMapping.applyCaseClass[Any](inputType)
        formWithInput(inputMapping).fill(x).asInstanceOf[Form[BaseRunnableSpec]]

      case x: RunnableSpec =>
        formWithNoInput.fill(x).asInstanceOf[Form[BaseRunnableSpec]]
    }

  // List

  override protected def listView = { implicit ctx =>
    (runnableViews.list(_, _)).tupled
  }

  // Save

  override protected def saveCall(
    runnableSpec: BaseRunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedTime = specWithFixedScheduledTime(runnableSpec)

    super.saveCall(specWithFixedTime).map { id =>
      scheduleOrCancel(id, specWithFixedTime); id
    }
  }

  // Update

  override protected def updateCall(
    runnableSpec: BaseRunnableSpec)(
    implicit request: AuthenticatedRequest[AnyContent]
  ) = {
    val specWithFixedTime = specWithFixedScheduledTime(runnableSpec)

    super.updateCall(specWithFixedTime).map { id =>
      scheduleOrCancel(id, specWithFixedTime); id
    }
  }

  private def specWithFixedScheduledTime(runnableSpec: BaseRunnableSpec): BaseRunnableSpec =
    runnableSpec match {
      case x: RunnableSpec => x.copy(
        scheduledTime = runnableSpec.scheduledTime.map(fillZeroes)
      )
      case x: InputRunnableSpec[_] => x.copy(
        scheduledTime = runnableSpec.scheduledTime.map(fillZeroes)
      )
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

  def getScriptInputForm(className: String) = scriptActionAux(className) { implicit request =>
    instance =>

      instance match {
        // input runnable
        case inputRunnable: InputRunnable[_] =>
          val (_, inputFields) = genericFormAndHtmlInputs(inputRunnable)
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
      val (form, inputFields) = genericFormAndHtmlInputs(inputRunnable)

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

  def execute(id: BSONObjectID) = restrictAny {
    implicit request =>
      repo.get(id).flatMap(_.fold(
        Future(NotFound(s"Runnable #${id.stringify} not found"))
      ) { runnableSpec =>
        val start = new Date()
        runnableExec(runnableSpec).map { _ =>

          val execTimeSec = (new Date().getTime - start.getTime) / 1000

          render {
            case Accepts.Html() => referrerOrHome().flashing("success" -> s"Runnable '${runnableSpec.runnableClassName}' has been executed in $execTimeSec sec(s).")
            case Accepts.Json() => Created(Json.obj("message" -> s"Runnable executed in $execTimeSec sec(s)", "name" -> runnableSpec.runnableClassName))
          }
        }.recover(handleExceptions("execute"))
      })
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

  private def genericFormAndHtmlInputs[T](
    inputRunnable: InputRunnable[T],
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) = {
    val form = genericForm(inputRunnable, fieldNamePrefix)
    val inputFieldsView = htmlInputs(inputRunnable, form, fieldNamePrefix)

    (form, inputFieldsView)
  }

  private def htmlInputs[T](
    inputRunnable: InputRunnable[T],
    form: Form[_],
    fieldNamePrefix: Option[String] = None)(
    implicit webContext: WebContext
  ) =
    inputRunnable match {
      // input runnable with a custom fields view
      case inputView: InputView[T] =>
        inputView.inputFields(fieldNamePrefix)(implicitly[WebContext])(form.asInstanceOf[Form[T]])

      // input runnable with a generic fields view
      case _ =>
        val nameFieldTypeMap = FieldUtil.caseClassTypeToFlatFieldTypes(inputRunnable.inputType).toMap
        runnableViews.genericFields(form, nameFieldTypeMap, None)
    }

  private def genericForm(
    inputRunnable: InputRunnable[_],
    fieldNamePrefix: Option[String] = None
  ): Form[Any] = {
    val mapping = GenericMapping.applyCaseClass[Any](inputRunnable.inputType, fieldNamePrefix = fieldNamePrefix)
    Form(mapping)
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

  private def scheduleOrCancel(
    id: BSONObjectID,
    runnableSpec: BaseRunnableSpec
  ) =
    if (runnableSpec.scheduled)
      runnableScheduler.schedule(runnableSpec.scheduledTime.get)(id)
    else
      runnableScheduler.cancel(id)
}
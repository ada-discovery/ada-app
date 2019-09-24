package controllers.samplesDocumentation

import java.io.{File, FileInputStream, FileOutputStream}
import java.nio.file.{Files, Paths}
import java.util.Date

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.SampleDocumentation
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.dataaccess.dataset.DataSetAccessorFactory
import org.ada.server.services.UserManager
import org.ada.web.controllers.BSONObjectIDStringFormatter
import org.ada.web.controllers.core.AdaCrudControllerImpl
import org.ada.web.models.security.DeadboltUser
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import org.incal.play.controllers._
import org.incal.play.security.AuthAction
import org.incal.play.security.SecurityUtil.toAuthenticatedAction
import org.incal.play.util.WebUtil.getRequestParamValueOptional
import play.api.data.Forms.{ignored, mapping, nonEmptyText, text}
import play.api.data.{Form, FormError}
import play.api.mvc.{Action, AnyContent, Request}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.SampleDocumentationRepo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Deprecated
class DocumentationController @Inject()(
    repo: SampleDocumentationRepo,
    val userManager: UserManager,
    dataSetSettingRepo: DataSetSettingRepo,
    dsaf: DataSetAccessorFactory,
    userRepo: UserRepo
) extends AdaCrudControllerImpl[SampleDocumentation, BSONObjectID](repo)
    with SubjectPresentRestrictedCrudController[BSONObjectID]
    with HasFormShowView[SampleDocumentation, BSONObjectID]
    with HasBasicFormEditView[SampleDocumentation, BSONObjectID]
    with HasListView[SampleDocumentation]
    with HasBasicFormCreateView[SampleDocumentation] {
    private implicit val idsFormatter = BSONObjectIDStringFormatter
    override protected type ShowViewData = (
        SampleDocumentation
        )
    override protected type ListViewData = (
        Page[(SampleDocumentation, String)],
            Seq[FilterCondition]
        )
    private lazy val importFolder = configuration.getString("datasetimport.import.folder").getOrElse {
        val folder = new java.io.File("sampleDocs/").getAbsolutePath
        val path = Paths.get(folder)
        if (!Files.exists(path)) Files.createDirectory(path)
        folder
    }
    override protected val homeCall = {
        routes.DocumentationController.listAll()
    }
    override protected[controllers] val form = Form(
        mapping(
            "id" -> ignored(Option.empty[BSONObjectID]),
            "dataSetId" -> nonEmptyText,
            "timeUpdated" -> ignored(new Date())
        )(SampleDocumentation.apply)(SampleDocumentation.unapply)
    )

    override def get(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
        restrictAdminOrUserCustomAny(isSamplesOwner(id))(toAuthenticatedAction(super.get(id)))

    override def edit(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
        restrictAdminOrUserCustomAny(isSamplesOwner(id))(toAuthenticatedAction(super.edit(id)))

    def download(dataSetId: String): play.api.mvc.Action[AnyContent] =
        restrictAdminOrUserCustomAny(isSampleOwnerOrHasPermission(dataSetId)) {
            implicit request => {
                val folder = new java.io.File("sampleDocs/" + dataSetId)
                folder.listFiles().head
                val file = folder.listFiles().head
                Future {
                    Ok.sendFile(file)
                }
            }
        }

    private def isSampleOwnerOrHasPermission(
        dataSetId: String
    )(
        deadboltUser: DeadboltUser,
        request: AuthenticatedRequest[Any]
    ): Future[Boolean] = {
        for {
            dataSetSettings <- dataSetSettingRepo.find(Seq("dataSetId" #== dataSetId))
        } yield {
            dataSetSettings.map(_.ownerId.get).toSeq.contains(deadboltUser.id.get) || deadboltUser.permissions.map(_.value).contains(s"DS:$dataSetId:createRequest")
        }
    }

    def getByDataSetId(dataSetId: String): play.api.mvc.Action[AnyContent] =
        restrictAdminOrPermissionAny(s"DS:$dataSetId:createRequest") {
            implicit request => {
                for {
                    existingDocumentation <- repo.find(Seq("dataSetId" #== dataSetId))
                } yield {
                    if (existingDocumentation.size > 0) {
                        Ok(views.html.samplesDocumentation.show(existingDocumentation.head))
                    } else {
                        Redirect(org.ada.web.controllers.routes.AppController.index()).flashing("errors" -> s"Sample Document for dataSet $dataSetId not available")
                    }

                }
            }
        }

    override def update(id: BSONObjectID): play.api.mvc.Action[AnyContent] =
        restrictAdminAny(noCaching = true)(toAuthenticatedAction(super.update(id)))

    override def updateCall(item: SampleDocumentation)(implicit request: AuthenticatedRequest[AnyContent]): Future[BSONObjectID] = {
        repo.update(item.copy(timeUpdated = new Date()))
    }

    override def delete(id: BSONObjectID): Action[AnyContent] =
        restrictAdminOrUserCustomAny(isSamplesOwner(id))(toAuthenticatedAction(super.delete(id)))

    private def isSamplesOwner(
        documentationId: BSONObjectID
    )(
        deadboltUser: DeadboltUser,
        request: AuthenticatedRequest[Any]
    ): Future[Boolean] = {
        for {
            existingDocumentation <- repo.get(documentationId)
            dataSetSettings <- dataSetSettingRepo.find(Seq("dataSetId" #== existingDocumentation.get.dataSetId))
        } yield {
            dataSetSettings.map(_.ownerId.get).toSeq.contains(deadboltUser.id.get)
        }
    }

    override def find(
        page: Int,
        orderBy: String,
        conditions: Seq[FilterCondition]
    ) = AuthAction { implicit request => {
        for {
            user <- currentUser()
            dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user.get.id))
            userCriteria = Seq("dataSetId" #-> dataSetSettings.map(_.dataSetId).toSeq)
            (items, count) <- getFutureItemsAndCountByUserCriteria(Some(page), orderBy, conditions, Nil, Some(pageLimit), userCriteria)
            viewData <- getListViewData(
                Page(items, page, page * pageLimit, count, orderBy),
                conditions
            )(request)
        } yield {
            implicit val req = request: Request[_]
            render {
                case Accepts.Html() => Ok(listViewWithContext(viewData))
                case Accepts.Json() => Ok(toJson(items))
            }
        }
    }.recover(handleFindExceptions)
    }

    private def getFutureItemsAndCountByUserCriteria(
        page: Option[Int],
        orderBy: String,
        filter: Seq[FilterCondition],
        projection: Seq[String],
        limit: Option[Int],
        criteria: Seq[Criterion[Any]]
    ): Future[(Traversable[SampleDocumentation], Int)] = {
        val sort = toSort(orderBy)
        val skip = page.zip(limit).headOption.map { case (page, limit) =>
            page * limit
        }
        for {
            items <- repo.find(criteria, sort, projection, limit, skip)
            count <- repo.count(criteria)
        } yield {
            (items, count)
        }
    }

    private def getDocumentNames(documentations: Traversable[SampleDocumentation])= {
        documentations.map( doc => {
            val dataSetDir = new File(importFolder + "/" + doc.dataSetId)
                if (dataSetDir.exists() && dataSetDir.isDirectory) {
                   Some(doc, dataSetDir.listFiles.filter(f => f.isFile && !f.getName.startsWith(".")).head.getName)
                } else {
                    None
                }
            }
    ).flatten
    }

    override protected def getListViewData(page: Page[SampleDocumentation], conditions: Seq[FilterCondition]) = { implicit request =>
        Future{
            (Page(getDocumentNames(page.items), page.page, page.offset, page.total, page.orderBy), conditions)
        }
    }

    override def listAll(orderBy: String) = AuthAction { implicit request => {
        for {
            user <- currentUser()
            dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user.get.id))
            userCriteria = Seq("dataSetId" #-> dataSetSettings.map(_.dataSetId).toSeq)
            (items, count) <- getFutureItemsAndCountByUserCriteria(None, orderBy, Nil, Nil, None, userCriteria)

            viewData <- getListViewData(
                Page(items, 0, 0, count, orderBy),
                Nil
            )(request)
        } yield {
            implicit val req = request: Request[_]

            render {
                case Accepts.Html() => Ok(listViewWithContext(viewData))
                case Accepts.Json() => Ok(toJson(items))
            }
        }
    }.recover(handleListAllExceptions)
    }

    override def saveCall(
        documentation: SampleDocumentation
    )(
        implicit request: AuthenticatedRequest[AnyContent]
    ): Future[BSONObjectID] = {
        for {
            user <- currentUser()
            dataSetSettings <- dataSetSettingRepo.find(Seq("dataSetId" #== documentation.dataSetId))
            userIsOwner = dataSetSettings.map(_.ownerId).toSeq.find(_ == user.get.id).getOrElse(
                throw new AdaException("You are not the owner of the dataset " + documentation.dataSetId)
            )
            existingDocumentation <- repo.find(Seq("dataSetId" #== documentation.dataSetId))
            documentationForDataSetIdExists <- repo.find(Seq("dataSetId" #== documentation.dataSetId))
            id <-
            if (documentationForDataSetIdExists.size == 0) {
                repo.save(documentation)
            } else {
                throw new AdaException("A documentation already exists for dataset id " + documentation.dataSetId)
            }
        } yield
            id
    }

    override protected def createView = { implicit ctx =>
        views.html.samplesDocumentation.create(_)
    }

    override protected def formFromRequest(
        implicit request: Request[AnyContent]
    ): Form[SampleDocumentation] = {
        val filledForm = super.formFromRequest(request)

        def addToForm(
            form: Form[SampleDocumentation],
            values: Map[String, String]
        ): Form[SampleDocumentation] =
            form.bind(form.data ++ values)

        if (!filledForm.hasErrors && filledForm.value.isDefined) {
            val sampleDocumentation = filledForm.value.get
            val extraValuesOrErrors = handleImportFiles(sampleDocumentation)
            val extraValues = extraValuesOrErrors.collect { case (param, Some(value)) => (param, value) }
            val extraErrors = extraValuesOrErrors.collect { case (param, None) => FormError(param, "error.required", param) }

            extraErrors.foldLeft(addToForm(filledForm, extraValues)) {
                _.withError(_)
            }
        } else
            filledForm
    }

    private def handleImportFiles(
        importInfo: SampleDocumentation
    )(
        implicit request: Request[AnyContent]
    ): Map[String, Option[String]] = {
        val subFolderName = importInfo.dataSetId

        def copyImportFile(name: String, file: File): String = {
            if (new java.io.File(importFolder).exists()) {
                val folderDelimiter = if (importFolder.endsWith("/")) "" else "/"
                val path = importFolder + folderDelimiter + subFolderName + "/" + name
                clearDirectory(path)
                copyFile(file, path)
                path
            } else
                throw new AdaException(s"Data set import folder $importFolder does not exist. Create one or override the setting 'datasetimport.import.folder' in custom.conf.")
        }

        def pathKeyValue(
            fileParamKey: String,
            pathParamKey: String
        )(
            implicit request: Request[AnyContent]
        ): (String, Option[String]) = {
            val path: Option[String] = getFile(fileParamKey, request).map(dataFile =>
                copyImportFile(dataFile._1, dataFile._2)
            ) match {
                case Some(path) => Some(path)
                case None => getRequestParamValueOptional(pathParamKey)
            }
            (pathParamKey, path)
        }


        def getFile(fileParamKey: String, request: Request[AnyContent]): Option[(String, java.io.File)] = {
            val dataFileOption = request.body.asMultipartFormData.flatMap(_.file(fileParamKey))
            dataFileOption.flatMap { dataFile =>
                if (dataFile.filename.nonEmpty)
                    Some((dataFile.filename, dataFile.ref.file))
                else
                    None
            }
        }

        def copyFile(src: File, location: String): Unit = {
            val dest = new File(location)
            val destFolder = dest.getCanonicalFile.getParentFile
            if (!destFolder.exists()) {
                destFolder.mkdirs()
            }
            new FileOutputStream(dest) getChannel() transferFrom(
                new FileInputStream(src) getChannel, 0, Long.MaxValue
            )
        }

        def clearDirectory(location: String): Unit = {
            val dest = new File(location)
            val destFolder = dest.getCanonicalFile.getParentFile
            if (destFolder.exists()) {
                destFolder.listFiles.foreach(_.delete())
            }
        }

        Seq(pathKeyValue("samples-document", "samples-document")).toMap
    }

    override protected def getFormShowViewData(requestId: BSONObjectID, form: Form[SampleDocumentation]): AuthenticatedRequest[_] => Future[ShowViewData] = {
        implicit request => {
            Future {
                (form.get)
            }
        }
    }

    override protected def showView = { implicit ctx =>
        views.html.samplesDocumentation.show(_)
    }

    override protected def editView = { implicit ctx =>
        views.html.samplesDocumentation.edit(_)
    }

    override protected def listView = { implicit ctx =>
        (views.html.samplesDocumentation.list(_, _)).tupled
    }
}
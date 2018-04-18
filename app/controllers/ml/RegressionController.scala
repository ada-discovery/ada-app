package controllers.ml

import java.util.Date
import javax.inject.Inject

import controllers.core._
import controllers.{AdminRestrictedCrudController, EnumFormatter, SeqFormatter}
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models._
import models.ml.TreeCore
import models.ml.classification._
import models.ml.regression._
import persistence.RepoTypes._
import play.api.data.Forms.{mapping, optional, _}
import play.api.data.format.Formats._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Result}
import play.twirl.api.Html
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.BSONFormats._
import util.SecurityUtil.{restrictAdminAnyNoCaching, restrictSubjectPresentAny}
import views.html.{layout, regression => view}
import controllers.ml.routes.{RegressionController => regressionRoutes}
import dataaccess.AscSort
import models.ml.classification.ValueOrSeq.ValueOrSeq
import play.api.libs.json.{JsArray, Json}
import services.DataSpaceService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RegressionController @Inject()(
    repo: RegressionRepo,
    dataSpaceService: DataSpaceService
  ) extends CrudControllerImpl[Regression, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[Regression, BSONObjectID]
    with HasFormShowEqualEditView[Regression, BSONObjectID] {

  private implicit val regressionSolverFormatter = EnumFormatter(RegressionSolver)
  private implicit val generalizedLinearRegressionLinkTypeFormatter = EnumFormatter(GeneralizedLinearRegressionLinkType)
  private implicit val generalizedLinearRegressionFamilyFormatter = EnumFormatter(GeneralizedLinearRegressionFamily)
  private implicit val generalizedLinearRegressionSolverFormatter = EnumFormatter(GeneralizedLinearRegressionSolver)
  private implicit val regressionTreeImpurityFormatter = EnumFormatter(RegressionTreeImpurity)
  private implicit val randomRegressionForestFeatureSubsetStrategyFormatter = EnumFormatter(RandomRegressionForestFeatureSubsetStrategy)
  private implicit val gbtRegressionLossTypeFormatter = EnumFormatter(GBTRegressionLossType)

  private implicit val intSeqFormatter = SeqFormatter.applyInt
  private implicit val doubleSeqFormatter = SeqFormatter.applyDouble

  private implicit val intEitherSeqFormatter = EitherSeqFormatter[Int]
  private implicit val doubleEitherSeqFormatter = EitherSeqFormatter[Double]

  protected val treeCoreMapping: Mapping[TreeCore] = mapping(
    "maxDepth" -> of[ValueOrSeq[Int]],
    "maxBins" -> of[ValueOrSeq[Int]],
    "minInstancesPerNode" -> of[ValueOrSeq[Int]],
    "minInfoGain" -> of[ValueOrSeq[Double]],
    "seed" -> optional(longNumber(min = 1))
  )(TreeCore.apply)(TreeCore.unapply)

  protected val linearRegressionForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "regularization" -> of[ValueOrSeq[Double]],
      "elasticMixingRatio" -> of[ValueOrSeq[Double]],
      "maxIteration" -> of[ValueOrSeq[Int]],
      "tolerance" -> of[ValueOrSeq[Double]],
      "fitIntercept" -> optional(boolean),
      "solver" -> optional(of[RegressionSolver.Value]),
      "standardization" -> optional(boolean),
      "aggregationDepth" -> of[ValueOrSeq[Int]],
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(LinearRegression.apply)(LinearRegression.unapply))

  protected val generalizedLinearRegressionForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "regularization" -> of[ValueOrSeq[Double]],
      "link" -> optional(of[GeneralizedLinearRegressionLinkType.Value]),
      "maxIteration" -> of[ValueOrSeq[Int]],
      "tolerance" -> of[ValueOrSeq[Double]],
      "fitIntercept" -> optional(boolean),
      "family" -> optional(of[GeneralizedLinearRegressionFamily.Value]),
      "solver" -> optional(of[GeneralizedLinearRegressionSolver.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(GeneralizedLinearRegression.apply)(GeneralizedLinearRegression.unapply))

  protected val regressionTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "impurity" -> optional(of[RegressionTreeImpurity.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(RegressionTree.apply)(RegressionTree.unapply))

  protected val randomRegressionForestForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "numTrees" -> of[ValueOrSeq[Int]],
      "subsamplingRate" -> of[ValueOrSeq[Double]],
      "impurity" -> optional(of[RegressionTreeImpurity.Value]),
      "featureSubsetStrategy" -> optional(of[RandomRegressionForestFeatureSubsetStrategy.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(RandomRegressionForest.apply)(RandomRegressionForest.unapply))

  protected val gradientBoostRegressionTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "maxIteration" -> of[ValueOrSeq[Int]],
      "stepSize" -> of[ValueOrSeq[Double]],
      "subsamplingRate" -> of[ValueOrSeq[Double]],
      "lossType" -> optional(of[GBTRegressionLossType.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(GradientBoostRegressionTree.apply)(GradientBoostRegressionTree.unapply))

  protected case class RegressionCreateEditViews[E <: Regression](
    name: String,
    val form: Form[E],
    viewElements: (Form[E], Messages) => Html)(
    implicit manifest: Manifest[E]
  ) extends CreateEditFormViews[E, BSONObjectID] {

    override protected[controllers] def fillForm(item: E) =
      form.fill(item)

    override protected[controllers] def createView = { implicit ctx =>
      form =>
        layout.create(
          name,
          form,
          viewElements(form, ctx.msg),
          controllers.ml.routes.RegressionController.save,
          controllers.ml.routes.RegressionController.listAll(),
          'enctype -> "multipart/form-data"
        )
    }

    override protected[controllers] def editView = { implicit ctx =>
      data =>
        layout.edit(
          name,
          data.form.errors,
          viewElements(data.form, ctx.msg),
          regressionRoutes.update(data.id),
          regressionRoutes.listAll(),
          Some(regressionRoutes.delete(data.id))
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      RegressionCreateEditViews[LinearRegression](
        "Linear Regression",
        linearRegressionForm,
        view.linearRegressionElements(_)(_)
      ),

      RegressionCreateEditViews[GeneralizedLinearRegression](
        "Generalized Linear Regression",
        generalizedLinearRegressionForm,
        view.generalizedLinearRegressionElements(_)(_)
      ),

      RegressionCreateEditViews[RegressionTree](
        "Regression Tree",
        regressionTreeForm,
        view.regressionTreeElements(_)(_)
      ),

      RegressionCreateEditViews[RandomRegressionForest](
        "Random Regression Forest",
        randomRegressionForestForm,
        view.randomRegressionForestElements(_)(_)
      ),

      RegressionCreateEditViews[GradientBoostRegressionTree](
        "Gradient Boost Regression Tree",
        gradientBoostRegressionTreeForm,
        view.gradientBoostRegressionTreeElements(_)(_)
      )
    )

  override protected val home = Redirect(routes.RegressionController.find())

  // default form... unused
  override protected[controllers] val form = linearRegressionForm.asInstanceOf[Form[Regression]]

  def create(concreteClassName: String) = restrictAdminAnyNoCaching(deadbolt) {
    implicit request =>

      def createAux[E <: Regression](x: CreateEditFormViews[E, BSONObjectID]): Future[Result] =
        x.getCreateViewData.map { viewData =>
          Ok(x.createView(implicitly[WebContext])(viewData))
        }

      createAux(getFormWithViews(concreteClassName))
  }

  override protected type ListViewData = (Page[Regression], Traversable[DataSpaceMetaInfo])

  override protected def getListViewData(page: Page[Regression]) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, tree)
  }

  override protected[controllers] def listView = { implicit ctx => (view.list(_, _)).tupled }

  def idAndNames = restrictSubjectPresentAny(deadbolt) {
    implicit request =>
      for {
        regressions <- repo.find(
          sort = Seq(AscSort("name"))
//          projection = Seq("concreteClass", "name", "timeCreated")
        )
      } yield {
        val idAndNames = regressions.map(regression =>
          Json.obj(
            "_id" -> regression._id,
            "name" -> regression.name
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
  }
}
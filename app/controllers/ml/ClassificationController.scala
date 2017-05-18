package controllers.ml

import java.util.Date
import javax.inject.Inject

import controllers.core._
import controllers.{AdminRestrictedCrudController, EnumFormatter, SeqFormatter, routes}
import dataaccess.AscSort
import dataaccess.RepoTypes.DataSpaceMetaInfoRepo
import models._
import models.ml.TreeCore
import models.ml.classification._
import persistence.RepoTypes._
import play.api.data.Forms.{mapping, optional, _}
import play.api.data.format.Formats._
import play.api.data.{Form, Mapping}
import play.api.i18n.Messages
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, Result}
import play.twirl.api.Html
import reactivemongo.play.json.BSONFormats._
import reactivemongo.bson.BSONObjectID
import services.DataSpaceService
import util.SecurityUtil.{restrictAdmin, restrictSubjectPresent}
import views.html.{layout, classification => view}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClassificationController @Inject()(
    repo: ClassificationRepo,
    dataSpaceService: DataSpaceService
  ) extends CrudControllerImpl[Classification, BSONObjectID](repo)
    with AdminRestrictedCrudController[BSONObjectID]
    with HasCreateEditSubTypeFormViews[Classification, BSONObjectID]
    with HasFormShowEqualEditView[Classification, BSONObjectID] {

  private implicit val logisticModelFamilyFormatter = EnumFormatter(LogisticModelFamily)
  private implicit val mlpSolverFormatter = EnumFormatter(MLPSolver)
  private implicit val decisionTreeImpurityFormatter = EnumFormatter(DecisionTreeImpurity)
  private implicit val featureSubsetStrategyFormatter = EnumFormatter(RandomForestFeatureSubsetStrategy)
  private implicit val gbtClassificationLossTypeFormatter = EnumFormatter(GBTClassificationLossType)
  private implicit val bayesModelTypeFormatter = EnumFormatter(BayesModelType)

  private implicit val intSeqFormatter = SeqFormatter.applyInt
  private implicit val doubleSeqFormatter = SeqFormatter.applyDouble

  protected val treeCoreMapping: Mapping[TreeCore] = mapping(
    "maxDepth" -> optional(number(min = 1)),
    "maxBins" -> optional(number(min = 1)),
    "minInstancesPerNode" -> optional(number(min = 1)),
    "minInfoGain" -> optional(of(doubleFormat)),
    "seed" -> optional(longNumber(min = 1))
  )(TreeCore.apply)(TreeCore.unapply)

  protected val logisticRegressionForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "regularization" -> optional(of(doubleFormat)),
      "elasticMixingRatio" -> optional(of(doubleFormat)),
      "maxIteration" -> optional(number(min = 1)),
      "tolerance" -> optional(of(doubleFormat)),
      "fitIntercept" -> optional(boolean),
      "family" -> optional(of[LogisticModelFamily.Value]),
      "standardization" -> optional(boolean),
      "aggregationDepth" -> optional(number(min = 1)),
      "threshold" -> optional(of(doubleFormat)),
      "thresholds" -> optional(of[Seq[Double]]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(LogisticRegression.apply)(LogisticRegression.unapply))

  protected val multiLayerPerceptronForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "layers" -> of[Seq[Int]],
      "maxIteration" -> optional(number(min = 1)),
      "tolerance" -> optional(of(doubleFormat)),
      "blockSize" -> optional(number(min = 1)),
      "solver" -> optional(of[MLPSolver.Value]),
      "seed" -> optional(longNumber(min = 1)),
      "stepSize" -> optional(of(doubleFormat)),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(MultiLayerPerceptron.apply)(MultiLayerPerceptron.unapply))

  protected val decisionTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "impurity" -> optional(of[DecisionTreeImpurity.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(DecisionTree.apply)(DecisionTree.unapply))

  protected val randomForestForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "numTrees" -> optional(number(min = 1)),
      "subsamplingRate" -> optional(of(doubleFormat)),
      "impurity" -> optional(of[DecisionTreeImpurity.Value]),
      "featureSubsetStrategy" -> optional(of[RandomForestFeatureSubsetStrategy.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(RandomForest.apply)(RandomForest.unapply))

  protected val gradientBoostTreeForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "core" -> treeCoreMapping,
      "maxIteration" -> optional(number(min = 1)),
      "stepSize" -> optional(of(doubleFormat)),
      "subsamplingRate" -> optional(of(doubleFormat)),
      "lossType" -> optional(of[GBTClassificationLossType.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(GradientBoostTree.apply)(GradientBoostTree.unapply))

  protected val naiveBayesForm = Form(
    mapping(
      "id" -> ignored(Option.empty[BSONObjectID]),
      "smoothing" -> optional(of(doubleFormat)),
      "modelType" -> optional(of[BayesModelType.Value]),
      "name" -> optional(nonEmptyText),
      "createdById" -> ignored(Option.empty[BSONObjectID]),
      "timeCreated" -> ignored(new Date())
    )(NaiveBayes.apply)(NaiveBayes.unapply))


  protected case class ClassificationCreateEditViews[E <: Classification](
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
          controllers.ml.routes.ClassificationController.save,
          controllers.ml.routes.ClassificationController.listAll(),
          'enctype -> "multipart/form-data"
        )
    }

    override protected[controllers] def editView = { implicit ctx =>
      data =>
        layout.edit(
          name,
          data.form.errors,
          viewElements(data.form, ctx.msg),
          controllers.ml.routes.ClassificationController.update(data.id),
          controllers.ml.routes.ClassificationController.listAll(),
          Some(controllers.ml.routes.ClassificationController.delete(data.id))
        )
    }
  }

  override protected val createEditFormViews =
    Seq(
      ClassificationCreateEditViews[LogisticRegression](
        "Logistic Regression (Classification)",
        logisticRegressionForm,
        view.logisticRegressionElements(_)(_)
      ),

      ClassificationCreateEditViews[MultiLayerPerceptron](
        "MultiLayer Perceptron (Classification)",
        multiLayerPerceptronForm,
        view.multilayerPerceptronElements(_)(_)
      ),

      ClassificationCreateEditViews[DecisionTree](
        "Decision Tree (Classification)",
        decisionTreeForm,
        view.decisionTreeElements(_)(_)
      ),

      ClassificationCreateEditViews[RandomForest](
        "Random Forest (Classification)",
        randomForestForm,
        view.randomForestElements(_)(_)
      ),

      ClassificationCreateEditViews[GradientBoostTree](
        "Gradient Boost Tree (Classification)",
        gradientBoostTreeForm,
        view.gradientBoostTreeElements(_)(_)
      ),

      ClassificationCreateEditViews[NaiveBayes](
        "Naive Bayes (Classification)",
        naiveBayesForm,
        view.naiveBayesElements(_)(_)
      )
    )

  override protected val home = Redirect(routes.ClassificationController.find())

  // default form... unused
  override protected[controllers] val form = logisticRegressionForm.asInstanceOf[Form[Classification]]

  def create(concreteClassName: String) = restrictAdmin(deadbolt) {
    Action.async { implicit request =>

      def createAux[E <: Classification](x: CreateEditFormViews[E, BSONObjectID]): Future[Result] =
        x.getCreateViewData.map { viewData =>
          Ok(x.createView(implicitly[WebContext])(viewData))
        }

      createAux(getFormWithViews(concreteClassName))
    }
  }

  override protected type ListViewData = (Page[Classification], Traversable[DataSpaceMetaInfo])

  override protected def getListViewData(page: Page[Classification]) = { request =>
    for {
      tree <- dataSpaceService.getTreeForCurrentUser(request)
    } yield
      (page, tree)
  }

  override protected[controllers] def listView = { implicit ctx => (view.list(_, _)).tupled }

  def idAndNames = restrictSubjectPresent(deadbolt) {
    Action.async { implicit request =>
      for {
        classifications <- repo.find(
          sort = Seq(AscSort("name"))
//          projection = Seq("concreteClass", "name", "timeCreated")
        )
      } yield {
        val idAndNames = classifications.map(classification =>
          Json.obj(
            "_id" -> classification._id,
            "name" -> classification.name
          )
        )
        Ok(JsArray(idAndNames.toSeq))
      }
    }
  }
}
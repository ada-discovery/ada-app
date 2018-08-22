package persistence

import javax.inject.Provider

import com.google.inject.{Inject, Key, TypeLiteral}
import com.google.inject.assistedinject.FactoryModuleBuilder
import com.sksamuel.elastic4s.ElasticClient
import dataaccess.RepoTypes.CategoryRepo
import dataaccess.elastic.{ElasticClientProvider, ElasticFormatAsyncCrudRepo, ElasticJsonCrudRepo}
import dataaccess.ignite.{CacheAsyncCrudRepoFactory, CacheAsyncCrudRepoProvider, JsonBinaryCacheAsyncCrudRepoFactory}
import dataaccess.mongo.dataset.{CategoryMongoAsyncCrudRepo, ClassificationResultMongoAsyncCrudRepo, RegressionResultMongoAsyncCrudRepo, FieldMongoAsyncCrudRepo}
import dataaccess._
import dataaccess.mongo._
import models.DataSetFormattersAndIds._
import models._
import net.codingwell.scalaguice.ScalaModule
import dataaccess.RepoTypes._
import persistence.RepoTypes._
import com.google.inject.name.Names
import models.ml.classification.Classification
import models.ml.regression.Regression
import models.ml.unsupervised.UnsupervisedLearning
import models.ml.unsupervised.UnsupervisedLearning.unsupervisedLearningFormat
import persistence.dataset._
import reactivemongo.bson.BSONObjectID
import persistence.RepoDef.Repo
import models.workspace.Workspace

private object RepoDef extends Enumeration {
  abstract class AbstractRepo[T: Manifest] extends super.Val {
    val named: Boolean
    val man: Manifest[T] = manifest[T]
  }

  case class Repo[T: Manifest](repo: T, named: Boolean = false) extends AbstractRepo[T]
  case class ProviderRepo[T: Manifest](provider: Provider[T], named: Boolean = false) extends AbstractRepo[T]

  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  import models.DataSetImportFormattersAndIds.{DataSetImportIdentity, dataSetImportFormat}
  import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
  import Workspace.WorkspaceFormat
  import models.DataSetFormattersAndIds.{dataSetSettingFormat, fieldFormat, dictionaryFormat, DataSpaceMetaInfoIdentity, DictionaryIdentity, FieldIdentity, DataSetSettingIdentity}

  val TranslationRepo = Repo[TranslationRepo](
    new MongoAsyncCrudRepo[Translation, BSONObjectID]("translations"))

  val MessageRepo = Repo[MessageRepo](
    new MongoAsyncStreamRepo[Message, BSONObjectID]("messages"))

  val UserSettingsRepo = Repo[UserSettingsRepo](
    new MongoAsyncCrudRepo[Workspace, BSONObjectID]("workspace"))

  val ClassificationRepo = Repo[ClassificationRepo](
    new MongoAsyncCrudRepo[Classification, BSONObjectID]("classifications"))

  val RegressionRepo = Repo[RegressionRepo](
    new MongoAsyncCrudRepo[Regression, BSONObjectID]("regressions"))

  val UnsupervisedLearningRepo = Repo[UnsupervisedLearningRepo](
    new MongoAsyncCrudRepo[UnsupervisedLearning, BSONObjectID]("unsupervisedLearnings"))

  val DictionaryRootRepo = Repo[DictionaryRootRepo](
    new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries"))

//  val MongoDataSpaceMetaInfoRepo = Repo[MongoAsyncCrudExtraRepo[DataSpaceMetaInfo, BSONObjectID]](
//    new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos"), true)

  //  val DataSpaceMetaInfoRepo = Repo[MongoAsyncCrudExtraRepo[DataSpaceMetaInfo, BSONObjectID]](
//    new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos"))

//  val DataSetSettingRepo = Repo[DataSetSettingRepo](
//    new MongoAsyncCrudRepo[DataSetSetting, BSONObjectID]("dataset_settings"))

//  val DataSetImportRepo = Repo[DataSetImportRepo](
//    new ElasticFormatAsyncCrudRepo[DataSetImport, BSONObjectID]("dataset_imports", "dataset_imports", true, true, true, true))

  val DataSetImportRepo = Repo[DataSetImportRepo](
    new MongoAsyncCrudRepo[DataSetImport, BSONObjectID]("dataset_imports"))

  // experimental distributed repos
//  val StudentDistRepo = Repo[StudentDistRepo](
//    new SparkMongoDistributedRepo[Student, BSONObjectID]("students"))

//  val LuxDistParkRepo = Repo[JsObjectDistRepo](
//    new SparkMongoDistributedRepo[JsObject, BSONObjectID]("luxpark"), true)
}

// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  import models.DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableDataSpaceMetaInfoFormat, serializableBSONObjectIDFormat, DataSetSettingIdentity}
  import models.User.{serializableUserFormat, UserIdentity}

  def configure = {

    implicit val formatId = serializableBSONObjectIDFormat

    bind[DataSetSettingRepo].toProvider(
      new CacheAsyncCrudRepoProvider[DataSetSetting, BSONObjectID]("dataset_settings")
    ).asEagerSingleton

    bind[UserRepo].toProvider(
      new CacheAsyncCrudRepoProvider[User, BSONObjectID]("users")
    ).asEagerSingleton

    bind[DataSpaceMetaInfoRepo].toProvider(
      new CacheAsyncCrudRepoProvider[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos")
    ).asEagerSingleton

    bind[ElasticClient].toProvider(new ElasticClientProvider).asEagerSingleton

    // bind the repos defined above
    RepoDef.values.foreach(bindRepo(_))

    bind[DataSetAccessorFactory].to(classOf[DataSetAccessorFactoryImpl]).asEagerSingleton

    // install JSON repo factories and its cached version
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsonCrudRepo]{}, classOf[MongoJsonCrudRepo])
      .build(Key.get(classOf[MongoJsonCrudRepoFactory], Names.named("MongoJsonCrudRepoFactory"))))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsonCrudRepo]{}, classOf[ElasticJsonCrudRepo])
      .build(Key.get(classOf[JsonCrudRepoFactory], Names.named("ElasticJsonCrudRepoFactory"))))

    bind[MongoJsonCrudRepoFactory]
      .annotatedWith(Names.named("CachedJsonCrudRepoFactory"))
      .to(classOf[JsonBinaryCacheAsyncCrudRepoFactory])

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[ClassificationResultRepo]{}, classOf[ClassificationResultMongoAsyncCrudRepo])
      .build(classOf[ClassificationResultRepoFactory]))

    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[RegressionResultRepo]{}, classOf[RegressionResultMongoAsyncCrudRepo])
      .build(classOf[RegressionResultRepoFactory]))

    // install data set meta info repo factory
//    install(new FactoryModuleBuilder()
//      .implement(new TypeLiteral[DataSetMetaInfoRepo]{}, classOf[DataSetMetaInfoSubordinateMongoAsyncCrudRepo])
//      .build(classOf[DataSetMetaInfoRepoFactory]))

    // install dictionary field repo factory

//    install(new FactoryModuleBuilder()
//      .implement(new TypeLiteral[FieldRepo]{}, classOf[DictionaryFieldMongoAsyncCrudRepo])
//      .build(classOf[FieldRepoFactory]))

    // install dictionary category repo factory
//    install(new FactoryModuleBuilder()
//      .implement(new TypeLiteral[CategoryRepo]{}, classOf[DictionaryCategoryMongoAsyncCrudRepo])
//      .build(classOf[CategoryRepoFactory]))
  }

  private def bindRepo[T](repo : Repo[T]) = {
    implicit val manifest = repo.man
    if (repo.named)
      bind[T]
        .annotatedWith(Names.named(repo.toString))
        .toInstance(repo.repo)
    else
      bind[T].toInstance(repo.repo)
  }
}
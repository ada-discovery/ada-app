package persistence

import javax.inject.Provider

import com.google.inject.{Inject, TypeLiteral, Key}
import com.google.inject.assistedinject.FactoryModuleBuilder
import dataaccess.RepoTypes.DictionaryCategoryRepo
import dataaccess.ignite.{CacheAsyncCrudRepoProvider, CacheAsyncCrudRepoFactory, JsonBinaryCacheAsyncCrudRepoFactory}
import dataaccess.mongo.dataset.{DictionaryFieldMongoAsyncCrudRepo, DictionaryCategoryMongoAsyncCrudRepo}
import dataaccess._
import dataaccess.mongo._
import models._
import net.codingwell.scalaguice.ScalaModule
import dataaccess.RepoTypes._
import persistence.RepoTypes._
import com.google.inject.name.Names
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

  import models.DataSetImportFormattersAndIds._
  import reactivemongo.play.json.BSONFormats.BSONObjectIDFormat
  import dataaccess.DataSetFormattersAndIds.{dataSetSettingFormat, fieldFormat, dictionaryFormat, dataSpaceMetaInfoFormat, DataSpaceMetaInfoIdentity, DictionaryIdentity, FieldIdentity, DataSetSettingIdentity}

  val TranslationRepo = Repo[TranslationRepo](
    new MongoAsyncCrudRepo[Translation, BSONObjectID]("translations"))

//  val UserRepo = Repo[UserRepo](
//    new MongoAsyncCrudRepo[User, BSONObjectID]("users"))

  val MessageRepo = Repo[MessageRepo](
    new MongoAsyncStreamRepo[Message, BSONObjectID]("messages"))

  val UserSettingsRepo = Repo[WorkspaceRepo](
    new MongoAsyncCrudRepo[Workspace, BSONObjectID]("workspace"))

  val DictionaryRootRepo = Repo[DictionaryRootRepo](
    new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries"))

  val DataSpaceMetaInfoRepo = Repo[DataSpaceMetaInfoRepo](
    new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos"))

//  val DataSetSettingRepo = Repo[DataSetSettingRepo](
//    new MongoAsyncCrudRepo[DataSetSetting, BSONObjectID]("dataset_settings"))

  val DataSetImportRepo = Repo[DataSetImportRepo](
    new MongoAsyncCrudRepo[DataSetImport, BSONObjectID]("dataset_imports"))

  // experimental distributed repos
  val StudentDistRepo = Repo[StudentDistRepo](
    new SparkMongoDistributedRepo[Student, BSONObjectID]("students"))

//  val LuxDistParkRepo = Repo[JsObjectDistRepo](
//    new SparkMongoDistributedRepo[JsObject, BSONObjectID]("luxpark"), true)
}

// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  import dataaccess.DataSetFormattersAndIds.{serializableDataSetSettingFormat, serializableBSONObjectIDFormat, DataSetSettingIdentity}
  import dataaccess.User.{serializableUserFormat, UserIdentity}

  def configure = {

    implicit val formatId = serializableBSONObjectIDFormat

    bind[DataSetSettingRepo].toProvider(new CacheAsyncCrudRepoProvider[DataSetSetting, BSONObjectID]("dataset_settings")).asEagerSingleton

    bind[UserRepo].toProvider(new CacheAsyncCrudRepoProvider[User, BSONObjectID]("users")).asEagerSingleton

    // bind the repos defined above
    RepoDef.values.foreach(bindRepo(_))

    bind[DataSetAccessorFactory].to(classOf[DataSetAccessorFactoryImpl]).asEagerSingleton

    // install data set meta info repo factory
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DataSetMetaInfoRepo]{}, classOf[DataSetMetaInfoSubordinateMongoAsyncCrudRepo])
      .build(classOf[DataSetMetaInfoRepoFactory]))

    // install JSON repo factory and its cached version
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsonCrudRepo]{}, classOf[JsonMongoCrudRepo])
      .build(Key.get(classOf[JsonCrudRepoFactory], Names.named("JsonCrudRepoFactory"))))

    bind[JsonCrudRepoFactory]
      .annotatedWith(Names.named("CachedJsonCrudRepoFactory"))
      .to(classOf[JsonBinaryCacheAsyncCrudRepoFactory])

    // install dictionary field repo factory

//    install(new FactoryModuleBuilder()
//      .implement(new TypeLiteral[DictionaryFieldRepo]{}, classOf[DictionaryFieldMongoAsyncCrudRepo])
//      .build(classOf[DictionaryFieldRepoFactory]))

    // install dictionary category repo factory
//    install(new FactoryModuleBuilder()
//      .implement(new TypeLiteral[DictionaryCategoryRepo]{}, classOf[DictionaryCategoryMongoAsyncCrudRepo])
//      .build(classOf[DictionaryCategoryRepoFactory]))

//    bind[DataSetAccessorFactory].to(classOf[DataSetAccessorMongoFactory]).asEagerSingleton
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
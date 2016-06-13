package persistence

import com.google.inject.TypeLiteral
import com.google.inject.assistedinject.FactoryModuleBuilder
import models.DataSetFormattersAndIds._
import models.DataSetImportInfoFormattersAndIds.{dataSetImportInfoFormat, DataSetImportInfoIdentity}
import models._
import net.codingwell.scalaguice.ScalaModule
import persistence.RepoTypes._
import com.google.inject.name.Names
import persistence.dataset._
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import persistence.RepoDef.Repo
import models.security.CustomUser
import models.workspace.Workspace

private object RepoDef extends Enumeration {
  case class Repo[T : Manifest](
    repo : T,
    named : Boolean = false
  ) extends super.Val {
    val man: Manifest[T] = manifest[T]
  }
  implicit def valueToRepo[T](x: Value) = x.asInstanceOf[Repo[T]]

  val TranslationRepo = Repo[TranslationRepo](
    new MongoAsyncCrudRepo[Translation, BSONObjectID]("translations"))

  val UserRepo = Repo[UserRepo](
    new MongoAsyncCrudRepo[CustomUser, BSONObjectID]("users"))

  val MessageRepo = Repo[MessageRepo](
    new MongoAsyncStreamRepo[Message, BSONObjectID]("messages"))

  val UserSettingsRepo = Repo[WorkspaceRepo](
    new MongoAsyncCrudRepo[Workspace, BSONObjectID]("workspace"))

  val DictionaryRootRepo = Repo[DictionaryRootRepo](
    new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries"))

  val DataSpaceMetaInfoRepo = Repo[DataSpaceMetaInfoRepo](
    new MongoAsyncCrudRepo[DataSpaceMetaInfo, BSONObjectID]("dataspace_meta_infos"))

  val DataSetSettingRepo = Repo[DataSetSettingRepo](
    new MongoAsyncCrudRepo[DataSetSetting, BSONObjectID]("dataset_setting"))    // TODO: rename to plural: "dataset_settings"

  val DataSetImportInfoRepo = Repo[DataSetImportInfoRepo](
    new MongoAsyncCrudRepo[DataSetImportInfo, BSONObjectID]("dataset_import_infos"))

  // experimental distributed repos
  val StudentDistRepo = Repo[StudentDistRepo](
    new SparkMongoDistributedRepo[Student, BSONObjectID]("students"))

  val LuxDistParkRepo = Repo[JsObjectDistRepo](
    new SparkMongoDistributedRepo[JsObject, BSONObjectID]("luxpark"), true)
}

// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  def configure = {

    // bind repos defined above
    RepoDef.values.foreach(bindRepo(_))

    // install data set meta info repo factory
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DataSetMetaInfoRepo]{}, classOf[DataSetMetaInfoSubordinateMongoAsyncCrudRepo])
      .build(classOf[DataSetMetaInfoRepoFactory]))

    // install JsObject repo factory
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[JsObjectCrudRepo]{}, classOf[JsObjectMongoCrudRepo])
      .build(classOf[JsObjectCrudRepoFactory]))

    // install dictionary field repo factory
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DictionaryFieldRepo]{}, classOf[DictionaryFieldSubordinateMongoAsyncCrudRepo])
      .build(classOf[DictionaryFieldRepoFactory]))

    // install dictionary category repo factory
    install(new FactoryModuleBuilder()
      .implement(new TypeLiteral[DictionaryCategoryRepo]{}, classOf[DictionaryCategorySubordinateMongoAsyncCrudRepo])
      .build(classOf[DictionaryCategoryRepoFactory]))

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
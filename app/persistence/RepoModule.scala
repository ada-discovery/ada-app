package persistence

import models.DataSetFormattersAndIds._
import models._
import net.codingwell.scalaguice.ScalaModule
import persistence.RepoTypes._
import com.google.inject.name.Names
import persistence.dataset.{DataSetAccessorMongoFactory, DataSetAccessorFactory}
import play.api.libs.json.JsObject
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo.json.BSONFormats._
import persistence.RepoDef.Repo
import models.security.CustomUser

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

  val DictionaryRootRepo = Repo[DictionaryRootRepo](
    new MongoAsyncCrudRepo[Dictionary, BSONObjectID]("dictionaries"))

  val DataSetMetaInfoRepo = Repo[DataSetMetaInfoRepo](
    new MongoAsyncCrudRepo[DataSetMetaInfo, BSONObjectID]("dataset_meta_infos"))

  // experimental distributed repos
  val StudentDistRepo = Repo[StudentDistRepo](
    new SparkMongoDistributedRepo[Student, BSONObjectID]("students"))

  val LuxDistParkRepo = Repo[JsObjectDistRepo](
    new SparkMongoDistributedRepo[JsObject, BSONObjectID]("luxpark"), true)
}

// repo module used to bind repo types/instances withing Guice IoC container
class RepoModule extends ScalaModule {

  def configure = {
    RepoDef.values.foreach(bindRepo(_))
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
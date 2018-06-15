package services

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.RepoTypes.{DataSetMetaInfoRepo, DataSpaceMetaInfoRepo}
import models.DataSpaceMetaInfo
import models.security.{SecurityRole, UserManager}
import persistence.RepoTypes.UserSettingsRepo
import play.api.i18n.MessagesApi
import play.api.mvc.Request
import security.AdaAuthConfig
import dataaccess.Criterion.Infix
import dataaccess.User

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[DataSpaceServiceImpl])
trait DataSpaceService {

  def getTreeForCurrentUser(
    request: Request[_]
  ): Future[Traversable[DataSpaceMetaInfo]]

  def getTreeForUser(
     user: User
  ): Future[Traversable[DataSpaceMetaInfo]]

  def getDataSpaceForCurrentUser(
    dataSpace: DataSpaceMetaInfo)(
    request: Request[_]
  ): Future[Option[DataSpaceMetaInfo]]
}

@Singleton
class DataSpaceServiceImpl @Inject() (
    val userManager: UserManager,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo
  ) extends DataSpaceService with AdaAuthConfig {

  private val r1 = """^[DS:](.*[.].*)""".r
  private val r2 = """^[DS:](.*[.].*[.])""".r
  private val r3 = """^[DS:](.*[.].*[.].*[.])""".r

  override def getTreeForCurrentUser(request: Request[_]) =
    for {
      currentUser <- currentUser(request)
      dataSpaces <- currentUser match {
        case None => Future(Traversable[DataSpaceMetaInfo]())
        case Some(user) => getTreeForUser(user)
      }
    } yield
      dataSpaces

  override def getTreeForUser(user: User) =
    for {
      dataSpaces <- {
        val isAdmin = user.roles.contains(SecurityRole.admin)

        if (isAdmin)
          allAsTree
        else {
          val dataSetIds = getUsersDataSetIds(user)
          allAsTree.map(_.map(filterRecursively(dataSetIds)).flatten)
        }
      }
    } yield
      dataSpaces

  override def getDataSpaceForCurrentUser(
    dataSpace: DataSpaceMetaInfo)(
    request: Request[_]
  ): Future[Option[DataSpaceMetaInfo]] =
    for {
      currentUser <- currentUser(request)
      foundChildren <- dataSpaceMetaInfoRepo.find(Seq("parentId" #== dataSpace._id))
    } yield
      currentUser.map { user =>
        dataSpace.children.clear()
        dataSpace.children.appendAll(foundChildren)
        val isAdmin = user.roles.contains(SecurityRole.admin)
        if (isAdmin)
          Some(dataSpace)
        else {
          val dataSetIds = getUsersDataSetIds(user)
          filterRecursively(dataSetIds)(dataSpace)
        }
      }.flatten

  private def getUsersDataSetIds(user: User) =
    user.permissions.map { permission =>
      val dotsCount = permission.count(_ == '.')
      if (permission.startsWith("DS:") && dotsCount > 0) {
        val output = if (dotsCount == 1) {
          permission
        } else {
          val parts = permission.split('.')
          parts(0) + "." + parts(1)
        }
        Some(output.substring(3))
      } else
        None
    }.flatten.toSet

  private def allAsTree: Future[Traversable[DataSpaceMetaInfo]] = {
    dataSpaceMetaInfoRepo.find().map { dataSpaces =>
      val idDataSpaceMap = dataSpaces.map( dataSpace => (dataSpace._id.get, dataSpace)).toMap
      dataSpaces.foreach { dataSpace =>
        val parent = dataSpace.parentId.map(idDataSpaceMap.get).flatten
        if (parent.isDefined) {
          parent.get.children.append(dataSpace)
        }
      }
      dataSpaces.filterNot(_.parentId.isDefined)
    }
  }

  def filterRecursively(
    acceptedDataSetIds: Set[String])(
    dataSpace: DataSpaceMetaInfo
  ): Option[DataSpaceMetaInfo] = {
    val newDataSetMetaInfos = dataSpace.dataSetMetaInfos.filter(
      info => acceptedDataSetIds.contains(info.id)
    )

    val newChildren = dataSpace.children.map(filterRecursively(acceptedDataSetIds)).flatten

    if (newDataSetMetaInfos.nonEmpty || newChildren.nonEmpty)
      Some(dataSpace.copy(dataSetMetaInfos = newDataSetMetaInfos, children = newChildren))
    else
      None
  }
}

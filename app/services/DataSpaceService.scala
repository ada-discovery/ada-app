package services

import javax.inject.{Inject, Named, Singleton}

import com.google.inject.ImplementedBy
import dataaccess.DataSetMetaInfoRepoFactory
import dataaccess.RepoTypes.{DataSetMetaInfoRepo, DataSpaceMetaInfoRepo}
import models.{DataSpaceMetaInfo, User}
import models.security.UserManager
import play.api.mvc.Request
import security.AdaAuthConfig
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.security.SecurityRole

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

  def unregister(
    dataSpaceInfo: DataSpaceMetaInfo,
    dataSetId: String
  ): Future[Unit]
}

@Singleton
class DataSpaceServiceImpl @Inject() (
    val userManager: UserManager,
    dataSpaceMetaInfoRepo: DataSpaceMetaInfoRepo,
    dataSetMetaInfoRepoFactory: DataSetMetaInfoRepoFactory
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

  override def unregister(
    dataSpaceInfo: DataSpaceMetaInfo,
    dataSetId: String
  ) =
    for {
      // remove a data set from the data space
      _ <- {
        val filteredDataSetInfos = dataSpaceInfo.dataSetMetaInfos.filterNot(_.id.equals(dataSetId))
        dataSpaceMetaInfoRepo.update(dataSpaceInfo.copy(dataSetMetaInfos = filteredDataSetInfos))
      }

      // remove a data set from the data set meta info repo
      _ <- {
        dataSpaceInfo.dataSetMetaInfos.find(_.id.equals(dataSetId)).map { dataSetInfoToRemove =>
          val dataSetMetaInfoRepo = dataSetMetaInfoRepoFactory(dataSpaceInfo._id.get)
          dataSetMetaInfoRepo.delete(dataSetInfoToRemove._id.get)
        }.getOrElse(
          // should never happen
          Future(())
        )
      }
    } yield
      ()
}

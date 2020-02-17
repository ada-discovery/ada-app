package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.{BatchOrderRequest, Role}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.models.User
import org.ada.web.models.security.DeadboltUser
import org.incal.core.dataaccess.Criterion._
import org.incal.play.security.SecurityRole
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, BatchOrderRequestSettingRepo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[BatchOrderServiceImpl])
trait BatchOrderService {
  type USER = DeadboltUser

  def getRequestIdUserRoles(
    items: Traversable[BatchOrderRequest],
    currentUser: User
  ): Future[Map[BSONObjectID, Traversable[Role.Value]]]

  def getAllowedUserIds(
    existingRequest: BatchOrderRequest
  ): Future[Map[Role.Value, Traversable[BSONObjectID]]]
}

@Singleton
class BatchOrderServiceImpl @Inject()(
  requestsRepo: BatchOrderRequestRepo,
  requestSettingRepo: BatchOrderRequestSettingRepo,
  dataSetSettingRepo: DataSetSettingRepo
) extends BatchOrderService {

  def getRequestIdUserRoles(
    requests: Traversable[BatchOrderRequest],
    user: User
  ): Future[Map[BSONObjectID, Traversable[Role.Value]]] =
    Future.sequence(requests.map(r => getUserRoles(r, user))).map(_.toMap)

  private def getUserRoles(
    request: BatchOrderRequest,
    user: User
  ): Future[(BSONObjectID, Traversable[Role.Value])] = {
    for {
      committeeIds <- getCommitteeIds(request)

      ownerId <- getOwnerId(request)
    } yield {
      val userId = user._id.get

      def getRoleIfFound(ids: Seq[BSONObjectID], role: Role.Value) =
        if (ids.contains(userId)) Some(role) else None

      (
        request._id.get,
        Seq(
          getRoleIfFound(committeeIds, Role.Committee),
          getRoleIfFound(Seq(request.createdById), Role.Requester),
          getRoleIfFound(Seq(ownerId).flatten, Role.Owner),
          getAdminRoleIfApplicable(user)
        ).flatten
      )
    }
  }

  def getAllowedUserIds(
    request: BatchOrderRequest
  ) =
    for {
      committeeIds <- getCommitteeIds(request)

      ownerId <- getOwnerId(request)
    } yield
      Map(
        Role.Committee -> committeeIds,
        Role.Requester -> Seq(request.createdById),
        Role.Owner -> Seq(ownerId).flatten
      )

  // maximum one batch order request setting expected
  private def getCommitteeIds(
    request: BatchOrderRequest
  ): Future[Seq[BSONObjectID]] =
    for {
      requestSetting <- requestSettingRepo.find(
        Seq("dataSetId" #== request.dataSetId),
        limit = Some(1)
      ).map(_.headOption)
    } yield
      requestSetting.map(_.committeeUserIds).getOrElse(Nil)

  // maximum one data set setting expected
  private def getOwnerId(
    request: BatchOrderRequest
  ): Future[Option[BSONObjectID]] =
    for {
      dataSetSetting <- dataSetSettingRepo.find(
        Seq("dataSetId" #== request.dataSetId),
        limit = Some(1)
      ).map(_.headOption)
    } yield
      dataSetSetting.flatMap(_.ownerId)

  private def getAdminRoleIfApplicable(user: User): Option[Role.Value] =
    if (user.roles.contains(SecurityRole.admin))
      Some(Role.Administrator)
    else
      None
}
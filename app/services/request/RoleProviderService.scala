package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.{BatchOrderRequest, Role}
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.models.User
import org.incal.play.security.SecurityRole
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{RequestSettingRepo, BatchOrderRequestRepo}
import scala.concurrent.Future

@ImplementedBy(classOf[RoleProviderServiceImpl])
trait RoleProviderService {
  def getRoles(request: BatchOrderRequest, user : Option[User]) : Future[(BSONObjectID, Traversable[Role.Value])]
  def getRolesMapping(requests: Traversable[BatchOrderRequest], user : Option[User]): Future[Map[BSONObjectID,Traversable[Role.Value]]]
  def isAdmin(userFuture : Option[User]) : Boolean
}

@Singleton
class RoleProviderServiceImpl @Inject() (
  committeeRepo: RequestSettingRepo,
  requestRepo: BatchOrderRequestRepo,
  dataSetRepo: DataSetSettingRepo
) extends UserIdByRoleProvider[Traversable[Role.Value]](committeeRepo: RequestSettingRepo, dataSetRepo: DataSetSettingRepo) with RoleProviderService {

  // TODO: Move as an aux inner function of "processIds"
  def getRoleIfApplicable(
                           ids: Traversable[BSONObjectID],
                           role: Role.Value,
                           userId: Option[BSONObjectID]
                         ): Option[Role.Value] = {

   // user.get._id.map(ids.exists(_))

    if (ids.exists(_ == userId.get)) { // TODO: education note: if you do match on Option with None and Some you can replace it with "map"
      Some(role)
    } else {
      None
    }
  }

  def getAdminRoleIfApplicable(user: Option[User]): Option[Role.Value] =
    if (isAdmin(user)) {
      Some(Role.Administrator)
    } else {
      None
    }

  override def processIds(
    requesterId: Traversable[BSONObjectID],
    committeeIds: Traversable[BSONObjectID],
    ownerIds: Traversable[BSONObjectID],
    batchRequest: BatchOrderRequest,
    user: Option[User]
  ): Traversable[Role.Value] = {
    val roleOptions = Seq(
      getRoleIfApplicable(committeeIds, Role.Committee, user.get._id),
      getRoleIfApplicable(requesterId, Role.Requester, user.get._id),
      getRoleIfApplicable(ownerIds, Role.Owner, user.get._id),
      getAdminRoleIfApplicable(user)
    )

    roleOptions.flatten
  }

  override def getRoles(
    request: BatchOrderRequest,
    user: Option[User]
  ) =
    getIdByRole(request, user).map( roles =>  (request._id.get, roles))

  override def getRolesMapping(
    requests: Traversable[BatchOrderRequest],
    user: Option[User]
  )=
    for {
       entries <- Future.sequence(requests.map(r => getRoles(r, user)))
    } yield {
      entries.toMap
    }

  override def isAdmin(user: Option[User]) =
    user.get.roles.contains(SecurityRole.admin)
}
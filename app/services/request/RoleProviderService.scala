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
    batchRequest: BatchOrderRequest, // TODO: Not used
    user: Option[User] // TODO: You're expecting a "userId"
  ): Option[Role.Value] =
    // TODO: replace with "exists"
    ids.find(u => u == user.get._id.get) match { // TODO: education note: if you do match on Option with None and Some you can replace it with "map"
      case None => None
      case Some(id) => Some(role)
    }

  // TODO: never do a pattern matching on a boolean condition... also too primitive to be a function on its own
  def getAdminRoleIfApplicable(user: Option[User]): Option[Role.Value] =
    isAdmin(user) match {
      case true => Some(Role.Administrator)
      case false => None
    }

  override def processIds(
    requesterId: Traversable[BSONObjectID],
    committeeIds: Traversable[BSONObjectID],
    ownerIds: Traversable[BSONObjectID],
    batchRequest: BatchOrderRequest,
    user: Option[User]
  ): Traversable[Role.Value] = {
   val roleOptions = Seq(
      getRoleIfApplicable(committeeIds, Role.Committee, batchRequest, user),
      getRoleIfApplicable(requesterId, Role.Requester, batchRequest, user),
      getRoleIfApplicable(ownerIds, Role.Owner, batchRequest, user),
      getAdminRoleIfApplicable(user)
    )

    roleOptions.filter(_.isDefined).map(_.get) // TODO: Do you mean flatten?
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
      entries.map{ e => (e._1, e._2)}.toMap // TODO: this should be "entries.toMap"
    }

  override def isAdmin(user: Option[User]) =
    user.get.roles.contains(SecurityRole.admin)
}
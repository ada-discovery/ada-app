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
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, RequestSettingRepo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[BatchOrderServiceImpl])
trait BatchOrderService {
    type USER = DeadboltUser

    def getUserRolesByRequest(items: Traversable[BatchOrderRequest], currentUser: Option[USER]): Future[Map[BSONObjectID, Traversable[Role.Value]]]

    def getAllowedUserIds(existingRequest: BatchOrderRequest, user: Option[User]): Future[Map[Role.Value, Traversable[BSONObjectID]]]
}

@Singleton
class BatchOrderServiceImpl @Inject()(
    requestsRepo: BatchOrderRequestRepo,
    requestSettingRepo: RequestSettingRepo,
    dataSetSettingRepo: DataSetSettingRepo
) extends BatchOrderService {

    def getUserRolesByRequest(requests: Traversable[BatchOrderRequest], user: Option[USER]): Future[Map[BSONObjectID, Traversable[Role.Value]]] = {
        Future.sequence(getRolesFutures(requests, user)).map(_.toMap)
    }

    private def getRolesFutures(requests: Traversable[BatchOrderRequest], user: Option[USER]): Traversable[Future[(BSONObjectID, Traversable[Role.Value])]] = {
        requests.map(r => getUserRoles(r, user))
    }

    private def getUserRoles(existingRequest: BatchOrderRequest, user: Option[USER]): Future[(BSONObjectID, Traversable[Role.Value])] = {
        for {
            committeeIds <- requestSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map {
                _.flatMap(_.userIds)
            }
            requesterId = Seq(existingRequest.createdById.getOrElse(
                throw new AdaException(("no requester id found for request: " + existingRequest._id.get))
            )
            )
            ownerIds <- dataSetSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map { dataSets =>
                dataSets.filter(dataSet => dataSet.ownerId.isDefined).map(dataSet => dataSet.ownerId.get)
            }
        } yield {
            (existingRequest._id.get,
                Seq(
                    getRoleIfApplicable(committeeIds, Role.Committee, user.get.user._id),
                    getRoleIfApplicable(requesterId, Role.Requester, user.get.user._id),
                    getRoleIfApplicable(ownerIds, Role.Owner, user.get.user._id),
                    getAdminRoleIfApplicable(user)
                ).flatten)
        }
    }

    private def getAdminRoleIfApplicable(user: Option[USER]): Option[Role.Value] =
        if (user.get.roles.contains(SecurityRole.admin)) {
            Some(Role.Administrator)
        } else {
            None
        }

    private def getRoleIfApplicable(
        ids: Traversable[BSONObjectID],
        role: Role.Value,
        userId: Option[BSONObjectID]
    ): Option[Role.Value] = {
        ids.find(_ == userId.get).map(id => role)
    }

    def getAllowedUserIds(existingRequest: BatchOrderRequest, user: Option[User]) =
        for {
            committeeIds <- requestSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map {
                _.flatMap(_.userIds)
            }
            requesterId = Traversable(existingRequest.createdById.getOrElse(
                throw new AdaException(("no requester id found for request: " + existingRequest._id.get))
            )
            )
            ownerIds <- dataSetSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map { dataSets =>
                dataSets.filter(dataSet => dataSet.ownerId.isDefined).map(dataSet => dataSet.ownerId.get)
            }
        } yield {
            Map(
                Role.Committee -> committeeIds,
                Role.Requester -> requesterId,
                Role.Owner -> ownerIds
            )
        }


}
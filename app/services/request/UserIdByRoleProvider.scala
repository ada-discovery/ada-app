package services.request

import javax.inject.Inject
import models.{BatchOrderRequest, Role}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.User

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{BatchOrderRequestRepo, RequestSettingRepo}
import org.incal.core.dataaccess.Criterion.Infix

// TODO: Why is this an abstract class with "committeeRepo" and "dataSetSettingRepo" as attributes? You are doing something here with two repos that the implementing class will inject anyway
abstract class UserIdByRoleProvider[T](committeeRepo: RequestSettingRepo, dataSetSettingRepo: DataSetSettingRepo) {

  def getIdByRole(existingRequest: BatchOrderRequest, user: Option[User])=
    for {
      committeeIds <-  committeeRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map { _.flatMap(_.userIds) }
      requesterId = Seq(existingRequest.createdById.getOrElse(
        throw new AdaException(("no requester id found for request: " + existingRequest._id.get)))
      )
      ownerIds <-   dataSetSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map{ dataSets =>
        dataSets.filter(dataSet => dataSet.ownerId.isDefined).map(dataSet => dataSet.ownerId.get)
      }
    } yield
      processIds(requesterId, committeeIds,ownerIds, existingRequest, user)

  // TODO: What is this function doing exactly? Also if not calleable directly should be always "protected"
  def processIds(
    requesterId: Traversable[BSONObjectID],
    committeeIds: Traversable[BSONObjectID],
    ownerIds: Traversable[BSONObjectID],
    existingRequest: BatchOrderRequest,
    user: Option[User]
  ): T
}

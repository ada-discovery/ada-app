package services.request

import javax.inject.Inject
import models.{BatchOrderRequest, Role}
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.User

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import org.incal.core.dataaccess.Criterion.Infix

abstract class UserIdByRoleProvider[T] (committeeRepo: ApprovalCommitteeRepo, dataSetSettingRepo: DataSetSettingRepo) {

def getIdByRole(existingRequest: BatchOrderRequest, user: Option[User])= {

  for{
    committeeIds <-  committeeRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map { _.flatMap(_.userIds) }
    requesterId = { Traversable(existingRequest.createdById.get)  }
    ownerIds <-   dataSetSettingRepo.find(Seq("dataSetId" #== existingRequest.dataSetId)).map{ dataSets => dataSets.map(dataSet => dataSet.ownerId.get) }
  }yield
    {
      processIds(requesterId,committeeIds,ownerIds, existingRequest, user)
    }
}

  def processIds(requesterId: Traversable[BSONObjectID], committeeIds: Traversable[BSONObjectID], ownerIds:  Traversable[BSONObjectID], existingRequest: BatchOrderRequest, user: Option[User]): T
}

package services.request

import javax.inject.Inject
import models.{BatchOrderRequest, Role, RoleByUserIdsMapping}
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo, UserRepo}
import org.ada.server.models.User
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

import scala.concurrent.Future


class UserIdByRoleProviderImpl @Inject()(committeeRepo: ApprovalCommitteeRepo,  dataSetSettingRepo: DataSetSettingRepo) extends UserIdByRoleProvider[Map[Role.Value, Traversable[BSONObjectID]]](committeeRepo, dataSetSettingRepo) {

  override def processIds(requesterId: Traversable[BSONObjectID], committeeIds: Traversable[BSONObjectID], ownerIds:  Traversable[BSONObjectID], existingRequest: BatchOrderRequest, user: Option[User]): Map[Role.Value, Traversable[BSONObjectID]] = {
   Map (
     Role.Committee -> committeeIds,
     Role.Requester -> requesterId,
     Role.Owner -> ownerIds
   )
 }

}

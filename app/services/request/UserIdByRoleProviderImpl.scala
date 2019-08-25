package services.request

import javax.inject.Inject
import models.{BatchOrderRequest, Role}
import org.ada.server.dataaccess.RepoTypes.{DataSetSettingRepo}
import org.ada.server.models.User
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{RequestSettingRepo}

class UserIdByRoleProviderImpl @Inject()(
  committeeRepo: RequestSettingRepo,
  dataSetSettingRepo: DataSetSettingRepo
) extends UserIdByRoleProvider[Map[Role.Value, Traversable[BSONObjectID]]](committeeRepo, dataSetSettingRepo) {

  override def processIds(
    requesterId: Traversable[BSONObjectID],
    committeeIds: Traversable[BSONObjectID],
    ownerIds:  Traversable[BSONObjectID],
    existingRequest: BatchOrderRequest,
    user: Option[User]
  ): Map[Role.Value, Traversable[BSONObjectID]] =
   Map (
     Role.Committee -> committeeIds,
     Role.Requester -> requesterId,
     Role.Owner -> ownerIds
   )
}

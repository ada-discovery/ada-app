package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.{BatchOrderRequest, Role}
import org.ada.server.AdaException
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.models.User
import org.ada.server.services.ml.MachineLearningServiceImpl
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.security.SecurityRole
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


@ImplementedBy(classOf[RoleProviderServiceImpl])
trait RoleProviderService {


  def getRole(request: BatchOrderRequest, user : Option[User]) : Role.Value
  def isAdmin(userFuture : Option[User]) : Boolean
}

@Singleton
class RoleProviderServiceImpl @Inject() (committeeRepo: ApprovalCommitteeRepo, requestRepo: BatchOrderRequestRepo, dataSetRepo: DataSetSettingRepo) extends UserIdByRoleProvider[Role.Value](committeeRepo: ApprovalCommitteeRepo, dataSetRepo: DataSetSettingRepo) with RoleProviderService {

  def getRoleFuture(request: BatchOrderRequest, user : Option[User]) = {

     isAdmin(user) match {
      case true => Future {
        Role.Administrator
      }
      case false => {
        getIdByRole(request, user)
      }
    }
  }

  override def processIds(requesterId: Traversable[BSONObjectID], committeeIds: Traversable[BSONObjectID], ownerIds:  Traversable[BSONObjectID], batchRequest: BatchOrderRequest, user: Option[User]) = {


    committeeIds.find(c => c == user.get._id.get) match {
      case None => {
        ownerIds.find(c => c == user.get._id.get) match {
          case None => {
            batchRequest.createdById.get == user.get._id.get match {
              case true => Role.Requester
              case _ => throw new AdaException("no role found for user id: " + user.get._id)
            }
          }
          case Some(id) => Role.Owner
        }
      }
      case _ => Role.Committee
    }

  }

  /*
  override def getCommitteeIds = {
    committeeRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId)).map {
      _.flatMap(_.userIds)
    }
  }



  def determineRole(isAdmin: Boolean, requestId: BSONObjectID, user: Option[User]): Future[Role.Value] = {


    getIdByRole(user)

    isAdmin match {
      case true => Future {
        Role.Administrator
      }
      case false => {
        for {
          batchRequest <- requestRepo.get(requestId)
          dataSetSetting <-  dataSetRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId))
          commiteeIds <- committeeRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId)).map {
            _.flatMap(_.userIds)
          }
          ownerId = dataSetSetting.toSeq(0).ownerId.get
        } yield {

        }
      }
    }
  }
*/
 override def getRole(request: BatchOrderRequest, user: Option[User]) = {
   Await.result(getRoleFuture(request, user), 10 seconds)
  }

  override def isAdmin(user : Option[User]) = {
      user.get.roles.contains(SecurityRole.admin)
  }
}

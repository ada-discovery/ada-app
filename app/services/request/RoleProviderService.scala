package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.Role
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


  def getRole(requestId: BSONObjectID, user : Option[User]) : Role.Value
  def isAdmin(userFuture : Option[User]) : Boolean
}

@Singleton
class RoleProviderServiceImpl @Inject() (committeeRepo: ApprovalCommitteeRepo, requestRepo: BatchOrderRequestRepo, dataSetRepo: DataSetSettingRepo) extends RoleProviderService {

  def getRoleFuture(requestId: BSONObjectID, user : Option[User]) = {
      determineRole(isAdmin(user), requestId, user)
  }

  def determineRole(isAdmin: Boolean, requestId: BSONObjectID, user: Option[User]): Future[Role.Value] = {



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
          commiteeIds.find(c => c == user.get._id.get) match {
            case None => {
              ownerId == user.get._id.get match {
                case false => {
                  batchRequest.get.createdById.get == user.get._id.get match {
                    case true => Role.Requester
                    case _ => throw new AdaException("no role found for user id: " + user.get._id)
                  }
                }
                case true => Role.Owner
              }
            }
            case _ => Role.Committee
          }
        }
      }
    }
  }

 override def getRole(requestId: BSONObjectID, user: Option[User]) = {
   Await.result(getRoleFuture(requestId, user), 10 seconds)
  }

  override def isAdmin(user : Option[User]) = {
      user.get.roles.contains(SecurityRole.admin)
  }
}

package services.request

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import models.Role
import org.ada.server.AdaException
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


  def getRole(requestId: BSONObjectID, userFuture : Option[User]) : Role.Value
  def isAdmin(userFuture : Option[User]) : Boolean
}

@Singleton
class RoleProviderServiceImpl @Inject() (committeeRepo: ApprovalCommitteeRepo, requestRepo: BatchOrderRequestRepo) extends RoleProviderService {

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
          commiteeIds <- committeeRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId)).map {
            _.flatMap(_.userIds)
          }
          ownerId = { Seq(BSONObjectID.parse("5cc2b4b0ea0100ec0159ab13").get) }
        } yield {
          commiteeIds.find(c => c == user.get._id.get) match {
            case None => {
              ownerId.find(c => c == user.get._id.get) match {
                case None => {
                  batchRequest.get.createdById.get == user.get._id.get match {
                    case true => Role.Requester
                    case _ => throw new AdaException("no role found for user id: " + user.get._id)
                  }
                }
                case _ => Role.Owner
              }
            }
            case _ => Role.Committee
          }
        }
      }
    }
  }

 override def getRole(requestId: BSONObjectID, userFuture : Option[User]) = {
   Await.result(getRoleFuture(requestId, userFuture), 10 seconds)
  }

  override def isAdmin(user : Option[User]) = {
      user.get.roles.contains(SecurityRole.admin)
  }
}

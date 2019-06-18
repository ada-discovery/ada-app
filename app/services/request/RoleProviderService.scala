package services.request

import models.Role
import org.ada.server.AdaException
import org.ada.server.models.User
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.security.SecurityRole
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

case class RoleProviderService(val userFuture: Future[Option[User]], val committeeRepo: ApprovalCommitteeRepo, val requestRepo: BatchOrderRequestRepo) {

  def getRoleFuture(requestId: BSONObjectID) = {

    for {
      isAdmin <- isAdmin()
      role <- determineRole(isAdmin, requestId)
    }
      yield {
        role
      }
  }

  def determineRole(isAdmin: Boolean, requestId: BSONObjectID) = {

    isAdmin match {
      case true => Future {
        Role.Administrator
      }
      case false => {
        for {
          user <- userFuture
          batchRequest <- requestRepo.get(requestId)
          commiteeIds <- committeeRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId)).map {
            _.flatMap(_.userIds)
          }
          ownerId <- Future {
            Seq(BSONObjectID.parse("5cc2b4b0ea0100ec0159ab13").get)
          }
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

  def getRole(requestId: BSONObjectID) = {
   Await.result(getRoleFuture(requestId), 10 seconds)
  }

  def isAdmin() = {
    for {
      user <- userFuture
    } yield {
      user.get.roles.contains(SecurityRole.admin)
    }
  }
}

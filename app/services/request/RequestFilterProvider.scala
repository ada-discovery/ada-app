package services.request

import be.objectify.deadbolt.scala.AuthenticatedRequest
import javax.inject.Inject
import models.{BatchOrderRequest, BatchRequestState}
import org.ada.server.models.User
import org.incal.core.FilterCondition
import org.incal.core.dataaccess.Criterion.Infix
import org.incal.play.Page
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.mailer.MailerClient
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}

import scala.concurrent.Future


class RequestFilterProvider @Inject()(committeeRepo: ApprovalCommitteeRepo, requestRepo: BatchOrderRequestRepo) {

  def filterForCurrentUser(isAdmin: Boolean, page: Page[BatchOrderRequest], conditions: Seq[FilterCondition],
                           currentUser:Option[User], getUsers: (Traversable[BatchOrderRequest]) => Future[Map[BSONObjectID,User]])={
    isAdmin match {
      case true => {
        getUsers(page.items).map(user => page.items.map(item => (item, user.get(item.createdById.get).get.ldapDn)))
      }
      case false => {
        for {
          filteredItems <- filterRelevant(page.items, currentUser)
          users <- getUsers(filteredItems.flatten.map(item => item))
          } yield {
         val submittedItems = filterSubmitted(filteredItems, currentUser)
         submittedItems.map(item => (item, users.get(item.createdById.get).get.ldapDn))
        }
      }
    }
  }

  def isExternalDraft(request: BatchOrderRequest, currentUser: Option[User]): Boolean = {
    request.state == BatchRequestState.Created && currentUser.get._id.get != request.createdById.get
  }

  def filterRelevant(requests: Traversable[BatchOrderRequest], currentUser: Option[User]): Future[Traversable[Option[BatchOrderRequest]]] = {
    Future.sequence( requests.map( r => {
      val isRelevantFuture = isUserRelevantFuture(r._id.get, currentUser)
       isRelevantFuture.map(
        isRelevant =>
          isRelevant match {
            case true => Some(r)
            case false => None
          }
        )
    }))
  }

  def filterSubmitted(requests: Traversable[Option[BatchOrderRequest]],currentUser: Option[User]): Traversable[BatchOrderRequest] = {
    requests.flatten.filter(r => !isExternalDraft(r, currentUser))
  }

  def isUserRelevantFuture(requestId: BSONObjectID, currentUser: Option[User]): Future[Boolean] = {
    for {
      batchRequest <- requestRepo.get(requestId)
      commiteeIds <- committeeRepo.find(Seq("dataSetId" #== batchRequest.get.dataSetId)).map {
        _.flatMap(_.userIds)
      }
      ownerId <- Future {
        Seq(BSONObjectID.parse("5cc2b4b0ea0100ec0159ab13").get)
      }
    } yield {
      commiteeIds.find(c => c == currentUser.get._id.get) match {
        case None => {
          ownerId.find(c => c == currentUser.get._id.get) match {
            case None => {
              batchRequest.get.createdById.get == currentUser.get._id.get match {
                case true => true
                case _ => false
              }
            }
            case _ => true
          }
        }
        case _ => true
      }
    }
  }
}

package services.request

import controllers.requests.BatchOrderRequestsController
import models.{BatchOrderRequest, BatchRequestState, Role}
import org.ada.server.AdaException
import org.ada.server.models.User
import org.incal.core.FilterCondition
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.{ApprovalCommitteeRepo, BatchOrderRequestRepo}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import org.incal.core.dataaccess.Criterion.Infix


case class RequestFilterProvider(val userFuture: Future[Option[User]], val committeeRepo: ApprovalCommitteeRepo, val requestRepo: BatchOrderRequestRepo) {


  def isExternalDraft(request: BatchOrderRequest, currentUser: Option[User]): Boolean = {
    request.state == BatchRequestState.Created && currentUser.get._id.get != request.createdById.get
  }


  def filterRelevant(requests: Traversable[BatchOrderRequest]): Future[Traversable[Option[BatchOrderRequest]]] = {
    Future.sequence( requests.map( r => {
      val isRelevantFuture = isUserRelevantFuture(r._id.get)
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

  def isUserRelevantFuture(requestId: BSONObjectID): Future[Boolean] = {
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

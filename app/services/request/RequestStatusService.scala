package services.request

import models.{BatchOrderRequest, BatchRequestState, Action}
import BatchRequestState._

class RequestStatusService {
  private val stateMapping: Map[BatchRequestState.Value,Traversable[(String, String)]] = buildTransitions()
  private val actionMapping: Map[BatchRequestState.Value,Actions] = buildActions()
  case class Actions(val accept:BatchRequestState.Value, val reject:Option[BatchRequestState.Value])

  def getNextStates(currentStatus : BatchRequestState.Value): Seq[(String,String)]={
    stateMapping.get(currentStatus).getOrElse(Seq()).toSeq
  }

  def getNextState(currentStatus : BatchRequestState.Value, action: Action.Value): BatchRequestState.Value = {
    action match {
      case Action.Approve  => actionMapping.get(currentStatus).get.accept
      case Action.Reject  => actionMapping.get(currentStatus).get.reject.get
    }
  }

  def buildTransitions()= {
    var transitions = Map[BatchRequestState.Value,Traversable[(String, String)]]()
    transitions += (BatchRequestState.Created -> buildTraversable(Seq(SentForApproval)))
    transitions += (BatchRequestState.SentForApproval -> buildTraversable(Seq(Rejected,Approved)))
    transitions += (BatchRequestState.Approved -> buildTraversable(Seq(OwnerAcknowledged, Unavailable)))
    transitions += (BatchRequestState.OwnerAcknowledged -> buildTraversable(Seq(Sent)))
    transitions += (BatchRequestState.Sent -> buildTraversable(Seq(UserReceived,NotReceived)))

    transitions
  }

  def buildActions()= {
    var transitions = Map[BatchRequestState.Value,Actions]()
    transitions += (BatchRequestState.Created -> Actions(SentForApproval,None))
    transitions += (BatchRequestState.SentForApproval -> Actions(Approved, Some(Rejected)))
    transitions += (BatchRequestState.Approved -> Actions(OwnerAcknowledged, Some(Unavailable)))
    transitions += (BatchRequestState.OwnerAcknowledged -> Actions(Sent, Some(Sent)))
    transitions += (BatchRequestState.Sent -> Actions(UserReceived,Some(NotReceived)))

    transitions
  }

  val map: Map[String, String] = Map(
    "a" -> "b",
    "c" -> "d"
  )



  def buildTraversable(states:Seq[BatchRequestState.Value])={
    states.map(s=>buildPair(s))
  }

  def buildPair(state: BatchRequestState.Value)={
    (state.toString,state.toString)
  }
}

object Transitions {
  val apply = Map()
}

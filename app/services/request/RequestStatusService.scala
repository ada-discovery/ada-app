package services.request

import models.{BatchOrderRequest, BatchRequestState}
import BatchRequestState._

class RequestStatusService {
  private val stateMapping: Map[BatchRequestState.Value,Traversable[(String, String)]] = buildTransitions()

  def getNextStates(currentStatus : BatchRequestState.Value): Seq[(String,String)]={
   return stateMapping.get(currentStatus).getOrElse(Seq()).toSeq
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

  def buildTraversable(states:Seq[BatchRequestState.Value])={
    states.map(s=>buildPair(s))
  }

  def buildPair(state: BatchRequestState.Value)={
    (state.toString,state.toString)
  }
}

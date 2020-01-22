package models

object RequestAction extends Enumeration {
  val Create, Submit, Withdraw, Approve, Reject, Send, NotAvailable, Receive, NotReceive  = Value
}

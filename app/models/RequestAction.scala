package models

// TODO: no need for the labels (which has just extra spaces)... you can use util.toHumanReadableCamel
object RequestAction extends Enumeration {
  val Create , Submit, Withdraw, Approve, Reject, Send = Value
  val NotAvailable =  Value("Not Available")
  val Receive =  Value("Received")
  val NotReceive = Value("Not Received")
}

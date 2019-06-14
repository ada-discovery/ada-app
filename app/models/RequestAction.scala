package models
import org.ada.web.controllers.EnumStringBindable


object RequestAction extends Enumeration {
  val Create , Submit, Withdraw, Approve, Reject, Send = Value
  val NotAvailable =  Value("Not Available")
  val Receive =  Value("Received")
  val NotReceive = Value("Not Received")
}

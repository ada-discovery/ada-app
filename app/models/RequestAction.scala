package models
import org.ada.web.controllers.EnumStringBindable


object RequestAction extends Enumeration {
  val Submit, Withdraw, Approve, Reject, NotAvailable, Send, Receive, NotReceive= Value
}

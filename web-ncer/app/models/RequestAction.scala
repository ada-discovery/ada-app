package models

object RequestAction extends Enumeration {
  val Create,   // Create request draft
  Submit,       // Submit draft for approval, rejection
  Approve,      // Approve request
  Reject,       // Reject request
  Send,         // Confirm sample shipping
  NotAvailable, // Decline request because samples are not available
  Receive       // Confirm receipt of samples
  = Value
}

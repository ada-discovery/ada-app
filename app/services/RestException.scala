package services

class RestException(message: String) extends Exception(message)

case class UnauthorizedAccessRestException(message: String) extends RestException(message)


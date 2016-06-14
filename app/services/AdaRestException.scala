package services

import models.AdaException

class AdaRestException(message: String) extends AdaException(message)

case class AdaUnauthorizedAccessRestException(message: String) extends AdaRestException(message)


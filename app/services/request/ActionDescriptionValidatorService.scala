package services.request

class ActionDescriptionValidatorService {

  val LENGTH_MIN = 2

   def validate(text: Option[String]) = {
   text.isDefined && text.get.length > LENGTH_MIN
  }
}



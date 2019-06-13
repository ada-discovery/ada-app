package services.request

class ActionDescriptionValidatorService {

  val LENGTH_MIN = 10

   def validate(text: String) = {
   text.length > LENGTH_MIN
  }
}



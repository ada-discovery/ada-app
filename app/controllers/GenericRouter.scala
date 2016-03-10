package controllers

import play.api.mvc.Call

protected class GenericRouter[T](protected val routes: T, paramName: String, id: String) {

  def routeFun(callFun: T => Call): Call =
    route(callFun(routes))

  def route(call: Call): Call = {
    val delimiter = if (call.url.contains("?")) "&" else "?"
    call.copy(url = call.url + delimiter + paramName + "=" + id)
  }
}
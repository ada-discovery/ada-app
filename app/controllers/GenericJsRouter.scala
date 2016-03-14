package controllers

import play.api.routing.JavaScriptReverseRoute

protected class GenericJsRouter[T](protected val routes: T, paramName: String, id: String) {

  def routeFun(callFun: T => JavaScriptReverseRoute): JavaScriptReverseRoute =
    route(callFun(routes))

  def route(call: JavaScriptReverseRoute): JavaScriptReverseRoute = {
    val jsFun = call.f
    val jsFunExprEnd = call.f.lastIndexOf(")")
    val param = " + _qS([(\"" + paramName + "=" + id + "\")])"
    val newJsFun = jsFun.substring(0, jsFunExprEnd - 1) + param + "})" + jsFun.substring(jsFunExprEnd + 1, jsFun.length)
    call.copy(f = newJsFun)
  }
}
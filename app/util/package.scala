import play.api.data.Form

package object util {

  def getDomainName(form: Form[_]) = form.get.getClass.getSimpleName.toLowerCase
}
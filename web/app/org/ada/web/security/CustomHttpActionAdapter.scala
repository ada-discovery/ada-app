package org.ada.web.security

import org.pac4j.core.context.HttpConstants
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.DefaultHttpActionAdapter
import play.mvc.{Result, Results}

class CustomHttpActionAdapter extends DefaultHttpActionAdapter {

  override def adapt(code: Int, context: PlayWebContext): Result = {
    if (code == HttpConstants.UNAUTHORIZED) {
      Results.unauthorized(views.html.error401.render().toString()).as(HttpConstants.HTML_CONTENT_TYPE)
    } else if (code == HttpConstants.FORBIDDEN) {
      Results.forbidden(views.html.error403.render().toString()).as(HttpConstants.HTML_CONTENT_TYPE)
    } else {
      super.adapt(code, context)
    }
  }

}

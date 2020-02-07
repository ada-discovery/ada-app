package runnables.irods

import javax.inject.Inject
import org.ada.web.services.RunnableHtmlOutputExt
import services.IRodsService
import views.html.table.displayTable
import org.ada.web.util.typeColumns
import org.irods.jargon.core.pub.domain.User
import java.text.SimpleDateFormat

class IrodsListUsers @Inject()(iRodsService: IRodsService) extends Runnable with RunnableHtmlOutputExt {

  private val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  override def run =
    addHtml(
      displayTable(
        iRodsService.listUsers.toSeq.sortBy(_.getId),
        typeColumns[User](
          (None, "Id", _.getId),
          (None, "Name", _.getName),
          (None, "Info", _.getInfo),
          (None, "Zone", _.getZone),
          (None, "Create Time", (user) => format.format(user.getCreateTime))
        )
      )
    )
}

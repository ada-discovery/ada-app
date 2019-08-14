package runnables.irods

import java.text.SimpleDateFormat

import javax.inject.Inject
import org.ada.web.services.RunnableHtmlOutputExt
import org.ada.web.util.typeColumns
import org.incal.core.runnables.InputRunnableExt
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry
import services.IRodsService
import views.html.table.displayTable

class IrodsListFolder @Inject()(iRodsService: IRodsService) extends InputRunnableExt[IrodsListFolderSpec] with RunnableHtmlOutputExt {

  private val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  override def run(input: IrodsListFolderSpec) =
    addHtml(
      displayTable(
        iRodsService.listFolderEntries(input.path.trim).toSeq.sortBy(_.getId),
        typeColumns[CollectionAndDataObjectListingEntry](
          (None, "Id", _.getId),
          (None, "Name", _.getPathOrName),
          (None, "Owner", _.getOwnerName),
          (None, "Size", _.getDataSize),
          (None, "Type", _.getObjectType),
          (None, "Create Time", (entry) => format.format(entry.getCreatedAt))
        )
      )
    )
}

case class IrodsListFolderSpec(path: String)

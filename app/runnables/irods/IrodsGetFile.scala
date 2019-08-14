package runnables.irods

import javax.inject.Inject
import org.ada.web.runnables.RunnableFileOutput
import org.incal.core.runnables.InputRunnableExt
import services.IRodsService

class IrodsGetFile @Inject()(iRodsService: IRodsService) extends InputRunnableExt[IrodsGetFileSpec] with RunnableFileOutput {

  override def run(input: IrodsGetFileSpec) = {
    setOutputByteSource(iRodsService.getFile(input.path.trim))
    setOutputFileName(input.path.trim.split("/").lastOption.getOrElse("irods_file"))
  }
}

case class IrodsGetFileSpec(path: String)

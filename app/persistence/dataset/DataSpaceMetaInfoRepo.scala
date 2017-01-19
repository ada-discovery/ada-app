package persistence.dataset

import dataaccess.RepoTypes._
import models.DataSpaceMetaInfo
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object DataSpaceMetaInfoRepo {

  def allAsTree(
    repo: DataSpaceMetaInfoRepo
  ): Future[Traversable[DataSpaceMetaInfo]] = {
    repo.find().map { dataSpaces =>
      val idDataSpaceMap = dataSpaces.map( dataSpace => (dataSpace._id.get, dataSpace)).toMap
      dataSpaces.foreach { dataSpace =>
        val parent = dataSpace.parentId.map(idDataSpaceMap.get).flatten
        if (parent.isDefined) {
          parent.get.children.append(dataSpace)
        }
      }
      dataSpaces.filterNot(_.parentId.isDefined)
    }
  }
}

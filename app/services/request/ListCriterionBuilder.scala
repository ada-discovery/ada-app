package services.request

import javax.inject.Inject
import models.BatchRequestState
import org.ada.server.dataaccess.RepoTypes.DataSetSettingRepo
import org.ada.server.models.User
import services.BatchOrderRequestRepoTypes.RequestSettingRepo
import org.incal.core.dataaccess.Criterion.Infix
import scala.concurrent.ExecutionContext.Implicits.global

class ListCriterionBuilder @Inject()(committeeRepo: RequestSettingRepo, dataSetSettingRepo: DataSetSettingRepo){

  def buildApproverCriterion(user: Option[User]) = {
    for {
      committees <- committeeRepo.find()
      dataSetSettings <- dataSetSettingRepo.find(Seq("ownerId" #== user.get._id))
    }
      yield {
        val userCommittee = committees.filter(c => c.userIds.contains(user.get._id.get))
        val committeeDataSetIds = userCommittee.map(c=>c.dataSetId).toSeq
        val ownedDataSetIds = dataSetSettings.map(s=>s.dataSetId).toSeq

        val dataSetIds = ownedDataSetIds ++ committeeDataSetIds

        Seq("dataSetId" #-> dataSetIds, "state" #!= BatchRequestState.Created.toString)
      }
  }

  def buildRequesterCriterion(user: Option[User]) = {
    Seq("createdById" #== user.get._id)
  }

}

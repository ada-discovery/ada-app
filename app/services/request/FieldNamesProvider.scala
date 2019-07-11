package services.request

import javax.inject.Inject
import org.incal.core.dataaccess.Criterion.Infix
import services.BatchOrderRequestRepoTypes.ApprovalCommitteeRepo
import scala.concurrent.ExecutionContext.Implicits.global

class FieldNamesProvider @Inject()(requestSettingRepo: ApprovalCommitteeRepo) {

 def getFieldNames(dataSetId: String) = {
    for{
      fieldNamesByDataSet <- requestSettingRepo.find(Seq("dataSetId" #== dataSetId)).map { _.flatMap(_.displayFieldNames) }
    } yield
      {
        fieldNamesByDataSet
      }
  }
 }
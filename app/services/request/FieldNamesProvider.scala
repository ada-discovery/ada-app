package services.request

import javax.inject.Inject
import org.incal.core.dataaccess.Criterion.Infix
import services.BatchOrderRequestRepoTypes.RequestSettingRepo
import scala.concurrent.ExecutionContext.Implicits.global

// TODO: Remove
class FieldNamesProvider @Inject()(requestSettingRepo: RequestSettingRepo) {

  def getFieldNames(dataSetId: String) =
    for {
      fieldNamesByDataSet <- requestSettingRepo.find(Seq("dataSetId" #== dataSetId)).map{ _.flatMap(_.displayFieldNames)}
    } yield
      fieldNamesByDataSet
}
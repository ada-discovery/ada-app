package services


import javax.inject.Inject
import models.ApprovalCommittee
import org.ada.server.models.DataSetFormattersAndIds.serializableBSONObjectIDFormat
import org.scalatest.{AsyncFlatSpec, Matchers}
import reactivemongo.bson.BSONObjectID

class ApprovalCommitteeModuleTest extends AsyncFlatSpec with Matchers with ExtraMatchers {




  "add new committee" should "success" in {
   // implicit val objectId = Some(BSONObjectID.parse("577e18c24500004800cdc557").get)
  //  val committee = ApprovalCommittee(objectId,"dataSetId")


  // var committeeRepo: ApprovalCommitteeRepo=new ApprovalCommitteeRepo()
   // committeeRepo.save(committee)



    assert(true===false)
  }



}

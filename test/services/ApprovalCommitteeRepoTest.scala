package services


import javax.inject.Inject
import models.ApprovalCommittee
import org.ada.server.models.DataSetFormattersAndIds.serializableBSONObjectIDFormat
import org.ada.server.services.StatsService
import org.scalatest.{AsyncFlatSpec, Matchers}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.ApprovalCommitteeRepo


import play.api.test._


class ApprovalCommitteeRepoTest extends AsyncFlatSpec with Matchers with ExtraMatchers {

  private val injector = TestApp(Seq("play.modules.reactivemongo.ReactiveMongoModule")).injector
  private val statsService = injector.instanceOf[BatchOrderRequestRepoTypes.ApprovalCommitteeRepo]

  "add new committee" should "success" in {
    assert(true===false)
  }
}

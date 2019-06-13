package services


import javax.inject.Inject
import models.ApprovalCommittee
import org.ada.server.models.DataSetFormattersAndIds.serializableBSONObjectIDFormat
import org.ada.server.services.{GuicePlayTestApp, StatsService}
import org.scalatest.{FlatSpec, Matchers}
import reactivemongo.bson.BSONObjectID
import services.BatchOrderRequestRepoTypes.ApprovalCommitteeRepo

import scala.concurrent.Future


class ApprovalCommitteeRepoTest extends FlatSpec with Matchers { // AsyncFlatSpec with

  private val injector = GuicePlayTestApp(Seq("play.modules.reactivemongo.ReactiveMongoModule")).injector
  private val statsService = injector.instanceOf[ApprovalCommitteeRepo]

  "add new committee" should "success" in {
//    "a" should startWith ("a")
  }
}

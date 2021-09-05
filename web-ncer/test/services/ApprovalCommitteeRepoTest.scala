package services

import org.ada.web.services.GuicePlayWebTestApp
import org.scalatest.{FlatSpec, Matchers}
import services.BatchOrderRequestRepoTypes.BatchOrderRequestSettingRepo

class ApprovalCommitteeRepoTest extends FlatSpec with Matchers { // AsyncFlatSpec with

  private val injector = GuicePlayWebTestApp(Seq("play.modules.reactivemongo.ReactiveMongoModule")).injector
  private val statsService = injector.instanceOf[BatchOrderRequestSettingRepo]

  "add new committee" should "success" in {
//    "a" should startWith ("a")
  }
}

package services

import org.scalatest.AsyncFlatSpec

class SampleRequestServiceSpec extends AsyncFlatSpec {

  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  private val sampleRequestService = InjectorWrapper.instanceOf[SampleRequestService]

  behavior of "SampleRequestService.getCatalogueItems"

  it should "return a list of catalogue items" in {
    for {
      items <- sampleRequestService.getCatalogueItems
    } yield assert(items.nonEmpty)
  }

}

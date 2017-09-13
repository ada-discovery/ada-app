package runnables.mpower

import javax.inject.Inject

import persistence.dataset.DataSetAccessorFactory
import runnables.FutureRunnable
import services.DataSetService
import util.seqFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CopyOldMPowerDataSets @Inject() (
    dataSetService: DataSetService,
    dsaf: DataSetAccessorFactory
  ) extends FutureRunnable {

  private val batchSize = 500

//  override def runAsFuture: Future[Unit] =
//    (
//      seqFutures((1 to 7)) { i =>
//        println("mpower_challenge.walking_activity_testing_rc_weights_" + i)
//        copy("mpower_challenge.walking_activity_testing_rc_weights_" + i, "mpower_challenge_old.walking_activity_testing_rc_weights_" + i)
//      }
//    ).flatMap(_ =>
//      seqFutures((1 to 93)) { i =>
//        println("mpower_challenge.walking_activity_training_norms_rc_weights_" + i)
//        copy("mpower_challenge.walking_activity_training_norms_rc_weights_" + i, "mpower_challenge_old.walking_activity_training_norms_rc_weights_" + i)
//      }
//    ).map(_ => ())

  override def runAsFuture: Future[Unit] =
    (
      seqFutures(
        Seq(
          ("mpower_challenge.walking_activity_training_results", "mpower_challenge.walking_activity_training_w_demographics_results")
        )) { case (from, to) =>
          println(to)
          copy(from, to)
        }
    ).map(_ => ())

  private def copy(sourceDataSetId: String, targetDataSetId: String) = {
    val targetDsa = dsaf(targetDataSetId).get

    for {
      setting <- targetDsa.setting
      // fields
      fields <- targetDsa.fieldRepo.find()

      sourceDataSetRepo <- {
        val fieldNamesAndTypes = fields.map(field => (field.name, field.fieldTypeSpec)).toSeq
        dsaf.dataSetRepoCreate(sourceDataSetId)(fieldNamesAndTypes, Some(setting))
      }

      // delete all target data
      _ <- targetDsa.dataSetRepo.deleteAll

      // get jsons
      jsons <- sourceDataSetRepo.find()

      // save records
      _ <- dataSetService.saveOrUpdateRecords(targetDsa.dataSetRepo, jsons.toSeq, None, false, None, Some(batchSize))

      // delete old records
      _ <- sourceDataSetRepo.deleteAll
    } yield
      ()
  }
}

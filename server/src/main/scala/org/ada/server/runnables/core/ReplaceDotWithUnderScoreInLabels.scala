package org.ada.server.runnables.core

import runnables.DsaInputFutureRunnable

import scala.concurrent.ExecutionContext.Implicits.global

class ReplaceDotWithUnderScoreInLabels extends DsaInputFutureRunnable[ReplaceDotWithUnderScoreInLabelsSpec] {

  override def runAsFuture(spec: ReplaceDotWithUnderScoreInLabelsSpec) =
    for {
      dsa <- createDsa(spec.dataSetId)

      // get all the fields
      fields <- dsa.fieldRepo.find()

      _ <- {
        val newFields = fields.map { field =>
          val newLabel = field.label.map(_.replaceAllLiterally("u002e", "_"))
          field.copy(label = newLabel)
        }
        dsa.fieldRepo.update(newFields)
      }
    } yield
      ()
}

case class ReplaceDotWithUnderScoreInLabelsSpec(dataSetId: String)
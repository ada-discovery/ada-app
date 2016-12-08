package controllers.dataset

import controllers.CrudController
import models.{CorrelationCalcSpec, ScatterCalcSpec, BoxCalcSpec, DistributionCalcSpec}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

trait DataViewController extends CrudController[BSONObjectID] {
  def idAndNames: Action[AnyContent]

  def getAndShowView(id: BSONObjectID): Action[AnyContent]

  def updateAndShowView(id: BSONObjectID): Action[AnyContent]

  def copy(id: BSONObjectID): Action[AnyContent]

  def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addScatter(
    dataViewId: BSONObjectID,
    xFieldName: String,
    yFieldName: String,
    groupFieldName: Option[String]
  ): Action[AnyContent]

  def addCorrelation(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]
}
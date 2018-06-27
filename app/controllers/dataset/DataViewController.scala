package controllers.dataset

import controllers.core.CrudController
import models.{BoxWidgetSpec, CorrelationWidgetSpec, DistributionWidgetSpec, ScatterWidgetSpec}
import play.api.mvc.{Action, AnyContent}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

trait DataViewController extends CrudController[BSONObjectID] {

  def idAndNames: Action[AnyContent]

  def idAndNamesAccessible: Action[AnyContent]

  def getAndShowView(id: BSONObjectID): Action[AnyContent]

  def updateAndShowView(id: BSONObjectID): Action[AnyContent]

  def copy(id: BSONObjectID): Action[AnyContent]

  def addDistributions(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addDistribution(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupName: Option[String]
  ): Action[AnyContent]

  def addCumulativeCount(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupName: Option[String]
  ): Action[AnyContent]

  def addCumulativeCounts(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addBoxPlots(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def addBoxPlot(
    dataViewId: BSONObjectID,
    fieldName: String,
    groupFieldName: Option[String]
  ): Action[AnyContent]

  def addBasicStats(
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

  def addIndependenceTest(
    dataViewId: BSONObjectID,
    fieldName: String,
    inputFieldNames: Seq[String]
  ): Action[AnyContent]

  def addTableFields(
    dataViewId: BSONObjectID,
    fieldNames: Seq[String]
  ): Action[AnyContent]

  def saveFilter(
    dataViewId: BSONObjectID,
    filterOrIds: Seq[Either[Seq[models.FilterCondition], BSONObjectID]]
  ): Action[AnyContent]
}
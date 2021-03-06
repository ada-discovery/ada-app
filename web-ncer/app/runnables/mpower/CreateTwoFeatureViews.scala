package runnables.mpower

import play.api.Logger
import runnables.DsaInputFutureRunnable
import org.ada.server.models._

import scala.reflect.runtime.universe.typeOf
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CreateTwoFeatureViews extends DsaInputFutureRunnable[CreateTwoFeatureViewsSpec] {

  private val logger = Logger // (this.getClass())

  private val defaultTableColumnNum = 11
  private val defaultWidgetNum = 100

  override def runAsFuture(input: CreateTwoFeatureViewsSpec) =
    for {
      // data set accessor
      dsa <- createDsa(input.dataSetId)
      fieldRepo = dsa.fieldRepo
      viewRepo = dsa.dataViewRepo

      // get the fields
      fields <- fieldRepo.find()

      // add rounding for the double fields (if needed) and introduce a default label
      _ <- if (input.adaptDictionary) {
        val newFields = fields.map { field =>
          val label = field.name.replaceAllLiterally("u002e", "_")
          field.fieldType match {
            case FieldTypeId.Double => field.copy(label = Some(label), displayDecimalPlaces = input.doubleDecimalPlaces)
            case _ => field.copy(label = Some(label))
          }
        }
        // it's faster to delete all the fields and then save rather then update them (one-by-one)
//          fieldRepo.update(newFields)
        fieldRepo.deleteAll.map(_ =>
          fieldRepo.save(newFields)
        )
      } else
        Future(())

      // remove old views if needed
      _ <- if (input.removeOldViews) viewRepo.deleteAll else Future(())

      // numeric field names
      numericFieldNames = fields.filter(field =>
        !field.name.equals(input.keyFieldName) && (field.fieldType == FieldTypeId.Double || field.fieldType == FieldTypeId.Integer)
      ).map(_.name).toSeq.sorted

      // create and save the views
      _ <- {
        val numericFieldNames = fields.filter(field =>
          !field.name.equals(input.keyFieldName) && (field.fieldType == FieldTypeId.Double || field.fieldType == FieldTypeId.Integer)
        ).map(_.name).toSeq.sorted

        val tableColumnFieldNames = (Seq(input.keyFieldName) ++ numericFieldNames).take(input.tableColumnNum.getOrElse(defaultTableColumnNum))
        val widgetFieldNames = numericFieldNames.take(input.widgetNum.getOrElse(defaultWidgetNum))

        viewRepo.save(Seq(
          distributionsView(tableColumnFieldNames, widgetFieldNames, input),
          tableOnlyView(tableColumnFieldNames, input)
        ))
      }

      // adapt the data set setting if needed
      _ <- if (input.adaptDataSetSetting)
        dsa.setting.flatMap { setting =>
          dsa.updateSetting(setting.copy(filterShowFieldStyle = Some(FilterShowFieldStyle.LabelsOnly), defaultDistributionFieldName = Some(numericFieldNames.head)))
        } else
          Future(())
    } yield
      ()

  private def distributionsView(
    tableColumnFieldNames: Seq[String],
    widgetFieldNames: Seq[String],
    spec: CreateTwoFeatureViewsSpec
  ): DataView = {
    val distributionDisplayOptions = MultiChartDisplayOptions(
      chartType = Some(ChartType.Column),
      gridWidth = spec.distributionWidgetGridWidth
    )

    val distributionWidgets = widgetFieldNames.map(
      DistributionWidgetSpec(_, None, displayOptions = distributionDisplayOptions)
    )

    val boxPlotWidgets = widgetFieldNames.map(
      BoxWidgetSpec(_, None, displayOptions = BasicDisplayOptions(gridWidth = spec.boxWidgetGridWidth))
    )

    DataView(
      None,
      "Distributions",
      Nil,
      tableColumnFieldNames,
      distributionWidgets ++ boxPlotWidgets,
      spec.defaultElementGridWidth,
      false,
      false
    )
  }

  private def tableOnlyView(
    tableColumnFieldNames: Seq[String],
    spec: CreateTwoFeatureViewsSpec
  ) = DataView(
      None,
      "Table Only",
      Nil,
      tableColumnFieldNames,
      Nil,
      spec.defaultElementGridWidth,
      true,
      false
    )
}

case class CreateTwoFeatureViewsSpec(
  dataSetId: String,
  doubleDecimalPlaces: Option[Int],
  defaultElementGridWidth: Int,
  distributionWidgetGridWidth: Option[Int],
  boxWidgetGridWidth: Option[Int],
  tableColumnNum: Option[Int],
  widgetNum: Option[Int],
  keyFieldName: String,
  adaptDictionary: Boolean,
  adaptDataSetSetting: Boolean,
  removeOldViews: Boolean
)
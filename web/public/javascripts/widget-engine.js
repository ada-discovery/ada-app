class WidgetEngine {

    // Main function of this class
    plot(widget, filterElement) {
        console.log("WidgetEngine.plot called")
        const widgetId = this._elementId(widget)
        this._plotWidgetForElement(widgetId, widget, filterElement)
    }

    _plotWidgetForElement(widgetId, widget, filterElement) {
        if (widget.displayOptions.isTextualForm)
            switch (widget.concreteClass) {
                case "org.ada.web.models.CategoricalCountWidget":
                    this._categoricalTableWidget(widgetId, widget);
                    break;
                case "org.ada.web.models.NumericalCountWidget":
                    this._numericalTableWidget(widgetId, widget);
                    break;
                case "org.ada.web.models.BasicStatsWidget":
                    this._basicStatsWidget(widgetId, widget);
                    break;
                default:
                    console.log(widget.concreteClass + " does not have a textual representation.")
            }
        else
            switch (widget.concreteClass) {
                case "org.ada.web.models.CategoricalCountWidget":
                    this._categoricalCountWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.NumericalCountWidget":
                    this._numericalCountWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.CategoricalCheckboxCountWidget":
                    this._categoricalChecboxCountWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.BoxWidget":
                    this._boxWidget(widgetId, widget);
                    break;
                case "org.ada.web.models.ScatterWidget":
                    this._scatterWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.ValueScatterWidget":
                    this._valueScatterWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.HeatmapWidget":
                    this._heatmapWidget(widgetId, widget);
                    break;
                case "org.ada.web.models.HtmlWidget":
                    this._htmlWidget(widgetId, widget);
                    break;
                case 'org.ada.web.models.LineWidget':
                    this._lineWidget(widgetId, widget, filterElement);
                    break;
                case "org.ada.web.models.BasicStatsWidget":
                    this._basicStatsWidget(widgetId, widget);
                    break;
                case "org.ada.web.models.IndependenceTestWidget":
                    this._independenceTestWidget(widgetId, widget);
                    break;
                default:
                    console.log("Widget type" + widget.concreteClass + " unrecognized.")
            }
    }

    _elementId(widget) {
        return widget._id.$oid + "Widget"
    }

    // creates a div for a widget
    widgetDiv(widget, defaultGridWidth, enforceWidth) {
        console.log("WidgetEngine.widgetDiv called")

        var elementIdVal = this._elementId(widget)

        if (enforceWidth)
            return this._widgetDivAux(elementIdVal, defaultGridWidth);
        else {
            var gridWidth = widget.displayOptions.gridWidth || defaultGridWidth;
            var gridOffset = widget.displayOptions.gridOffset;

            return this._widgetDivAux(elementIdVal, gridWidth, gridOffset);
        }
    }

    _widgetDivAux(elementIdVal, gridWidth, gridOffset) {
        var gridWidthElement = "col-md-" + gridWidth
        var gridOffsetElement = gridOffset ? "col-md-offset-" + gridOffset : ""

        var innerDiv = '<div id="' + elementIdVal + '" class="chart-holder"></div>'
        var div = $("<div class='" + gridWidthElement + " " + gridOffsetElement + "'>")
        div.append(innerDiv)
        return div
    }

    // impls hooks

    _categoricalCountWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _numericalCountWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _categoricalChecboxCountWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _boxWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _scatterWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _valueScatterWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _heatmapWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _htmlWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _lineWidget(widgetId, widget, filterElement) {
        throw "no fun impl. provided"
    }

    _independenceTestWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _categoricalTableWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _numericalTableWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }

    _basicStatsWidget(widgetId, widget) {
        throw "no fun impl. provided"
    }
}
class HighchartsWidgetEngine {

    _elementId(widget) {
        return widget._id.$oid + "Widget"
    }

    _agg(series, widget) {
        var counts = series.map(function (item) {
            return item.count;
        });

        if (widget.isCumulative) {
            var max = counts.reduce(function (a, b) {
                return Math.max(a, b);
            });

            return max
        } else {
            var sum = counts.reduce(function (a, b) {
                return a + b;
            });

            return sum
        }
    }

    _categoricalCountWidget(elementId, widget, filterElement) {
        const chartingEngine = new ChartingEngine()

        var categories = (widget.data.length > 0) ?
            widget.data[0][1].map(function (count) {
                return count.value
            })
            : []

        const that = this

        var datas = widget.data.map(function (nameSeries) {
            var name = nameSeries[0]
            var series = nameSeries[1]

            var sum = that._agg(series, widget)
            var data = series.map(function (item) {
                var label = shorten(item.value)
                var count = item.count
                var key = item.key

                var value = (widget.useRelativeValues) ? 100 * count / sum : count
                return {name: label, y: value, key: key}
            })

            return {name: name, data: data}
        })

        var totalCounts = widget.data.map(function (nameSeries) {
            return that._agg(nameSeries[1], widget);
        })

        var seriesSize = datas.length
        var height = widget.displayOptions.height || 400

        var pointFormat = function () {
            return (widget.useRelativeValues) ? that._categoricalPercentPointFormat(this) : that._categoricalCountPointFormat(totalCounts, this);
        }
        var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            chartingEngine.plotCategoricalChart({
                chartType,
                categories,
                datas,
                seriesSize,
                title: widget.title,
                yAxisCaption,
                chartElementId: elementId,
                showLabels: widget.showLabels,
                showLegend: widget.showLegend,
                height,
                pointFormat
            });
        }


        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType);
        })

        plot(widget.displayOptions.chartType)

        if (filterElement) {
            $('#' + elementId).on('pointSelected', function (event, data) {
                var condition = {fieldName: widget.fieldName, conditionType: "=", value: data.key};

                $(filterElement).multiFilter('replaceWithConditionAndSubmit', condition);
            });
        }
    }

    _numericalCountWidget(elementId, widget, filterElement) {
        const chartingEngine = new ChartingEngine()

        var isDate = widget.fieldType == "Date"
        var isDouble = widget.fieldType == "Double"
        var dataType = (isDate) ? 'datetime' : null;

        const that = this

        var datas = widget.data.map(function (nameSeries) {
            var name = nameSeries[0]
            var series = nameSeries[1]

            var sum = that._agg(series, widget)
            var data = series.map(function (item) {
                var count = item.count

                var y = (widget.useRelativeValues) ? 100 * count / sum : count
                return [item.value, y]
            })

            return {name: name, data: data}
        })

        var totalCounts = widget.data.map(function (nameSeries) {
            return that._agg(nameSeries[1], widget);
        })

        var seriesSize = datas.length
        var height = widget.displayOptions.height || 400

        var pointFormat = function () {
            return (widget.useRelativeValues) ? that._numericalPercentPointFormat(isDate, isDouble, this) : that._numericalCountPointFormat(isDate, isDouble, totalCounts, this);
        }
        var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            chartingEngine.plotNumericalChart({
                chartType,
                datas,
                seriesSize,
                title: widget.title,
                xAxisCaption: widget.fieldLabel,
                yAxisCaption,
                chartElementId: elementId,
                height,
                pointFormat,
                dataType
            })
        }

        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType)
        });

        plot(widget.displayOptions.chartType)

        if (filterElement) {
            this._addIntervalSelected($('#' + elementId), filterElement, widget.fieldName, isDouble, isDate)
        }
    }

    _lineWidget(elementId, widget, filterElement) {
        const chartingEngine = new ChartingEngine()

        var isDate = widget.xFieldType == "Date"
        var isDouble = widget.xFieldType == "Double"
        var xDataType = (isDate) ? 'datetime' : null;

        var isYDate = widget.yFieldType == "Date"
        var yDataType = (isYDate) ? 'datetime' : null;

        var datas = widget.data.map(function (nameSeries) {
            return {name: nameSeries[0], data: nameSeries[1]}
        })

        var seriesSize = datas.length
        var showLegend = seriesSize > 1

        var height = widget.displayOptions.height || 400

        const that = this
        var pointFormat = function () {
            return that._numericalPointFormat(isDate, isDouble, this, 3, 3);
        }

        // $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        //     plotNumericalChart(chartType, datas, seriesSize, widget.title, widget.xAxisCaption, yAxisCaption, elementId, height, pointFormat, dataType)
        // });

        chartingEngine.lineChart({
            title: widget.title,
            chartElementId: elementId,
            categories: null,
            series: datas,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            showLegend,
            enableDataLabels: true,
            pointFormat,
            height,
            xDataType,
            yDataType,
            allowPointSelectionEvent: false,
            allowIntervalSelectionEvent: true,
            allowChartTypeChange: false,
            xMin: widget.xMin,
            xMax: widget.xMax,
            yMin: widget.yMin,
            yMax: widget.yMax
        });

        if (filterElement) {
            this._addIntervalSelected($('#' + elementId), filterElement, widget.xFieldName, isDouble, isDate)
        }
    }

    _addIntervalSelected(chartElement, filterElement, xFieldName, isDouble, isDate) {
        function toTypedStringValue(value, ceiling) {
            var intValue = (ceiling) ? Math.ceil(value) : Math.floor(value)

            return (isDate) ? msToStandardDateString(intValue) : (isDouble) ? value.toString() : intValue.toString()
        }

        chartElement.on('intervalSelected', function (event, data) {
            var xMin = toTypedStringValue(data.xMin, true);
            var xMax = toTypedStringValue(data.xMax, false);

            var conditions = [
                {fieldName: xFieldName, conditionType: ">=", value: xMin},
                {fieldName: xFieldName, conditionType: "<=", value: xMax}
            ]

            $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
        });
    }

    _boxWidget(elementId, widget) {
        const chartingEngine = new ChartingEngine()

        var isDate = widget.fieldType == "Date"
        var dataType = (isDate) ? 'datetime' : null;

        var datas = widget.data.map(function (namedQuartiles) {
            var quartiles = namedQuartiles[1]
            return [quartiles.lowerWhisker, quartiles.lowerQuantile, quartiles.median, quartiles.upperQuantile, quartiles.upperWhisker]
        })

        var categories = widget.data.map(function (namedQuartiles) {
            return namedQuartiles[0]
        })

        var min = widget.min
        var max = widget.max

        var pointFormat = (isDate) ?
            '- Upper 1.5 IQR: {point.high:%Y-%m-%d}<br/>' +
            '- Q3: {point.q3:%Y-%m-%d}<br/>' +
            '- Median: {point.median:%Y-%m-%d}<br/>' +
            '- Q1: {point.q1:%Y-%m-%d}<br/>' +
            '- Lower 1.5 IQR: {point.low:%Y-%m-%d}<br/>'
            :
            '- Upper 1.5 IQR: {point.high}<br/>' +
            '- Q3: {point.q3}<br/>' +
            '- Median: {point.median}<br/>' +
            '- Q1: {point.q1}<br/>' +
            '- Lower 1.5 IQR: {point.low}<br/>'

        var height = widget.displayOptions.height || 400
        chartingEngine.boxPlot({
            title: widget.title,
            chartElementId: elementId,
            categories,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: datas,
            min,
            max,
            pointFormat,
            height,
            dataType
        })
    }

    _scatterWidget(elementId, widget, filterElement) {
        const chartingEngine = new ChartingEngine()

        var datas = widget.data.map(function (series) {
            return {name: shorten(series[0]), data: series[1]}
        })

        var height = widget.displayOptions.height || 400;

        var isXDate = widget.xFieldType == "Date"
        var xDataType = (isXDate) ? 'datetime' : null;

        var isYDate = widget.yFieldType == "Date"
        var yDataType = (isYDate) ? 'datetime' : null;

        if (filterElement) {
            this._addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
        }

        chartingEngine.scatterChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            series: datas,
            showLegend: true,
            pointFormat: null,
            height,
            xDataType,
            yDataType,
            zoomIfDragged: true,
            allowSelectionEvent: filterElement != null
        })
    }

    _addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate) {
        $('#' + elementId).on('areaSelected', function (event, data) {
            var xMin = (isXDate) ? msToStandardDateString(data.xMin) : data.xMin.toString();
            var xMax = (isXDate) ? msToStandardDateString(data.xMax) : data.xMax.toString();
            var yMin = (isYDate) ? msToStandardDateString(data.yMin) : data.yMin.toString();
            var yMax = (isYDate) ? msToStandardDateString(data.yMax) : data.yMax.toString();

            var conditions = [
                {fieldName: widget.xFieldName, conditionType: ">=", value: xMin},
                {fieldName: widget.xFieldName, conditionType: "<=", value: xMax},
                {fieldName: widget.yFieldName, conditionType: ">=", value: yMin},
                {fieldName: widget.yFieldName, conditionType: "<=", value: yMax}
            ]

            $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
        });
    }

    _valueScatterWidget(elementId, widget, filterElement) {
        const chartingEngine = new ChartingEngine()

        var zs = widget.data.map(function (point) {
            return point[2];
        })

        var zMin = Math.min.apply(null, zs);
        var zMax = Math.max.apply(null, zs);

        var data = widget.data.map(function (point) {
            var zColor = (1 - Math.abs((point[2] - zMin) / zMax)) * 210;
            return {x: point[0], y: point[1], z: point[2], color: 'rgba(255, ' + zColor + ',' + zColor + ', 0.8)'};
        })

        var datas = [{data: data}];

        var height = widget.displayOptions.height || 400;

        var isXDate = widget.xFieldType == "Date";
        var xDataType = (isXDate) ? 'datetime' : null;

        var isYDate = widget.yFieldType == "Date";
        var yDataType = (isYDate) ? 'datetime' : null;

        if (filterElement) {
            this._addScatterAreaSelected(elementId, filterElement, widget, isXDate, isYDate);
        }

        var pointFormatter = function () {
            var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x;
            var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y;
            var zPoint = this.point.z;

            return xPoint + ", " + yPoint + " (" + zPoint + ")";
        }

        chartingEngine.scatterChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            series: datas,
            showLegend: false,
            pointFormat: pointFormatter,
            height,
            xDataType,
            yDataType,
            zoomIfDragged: true,
            allowSelectionEvent: filterElement != null
        })
    }

    _heatmapWidget(elementId, widget) {
        const chartingEngine = new ChartingEngine()

        const xCategories = widget.xCategories
        const yCategories = widget.yCategories
        const data = widget.data.map(function (seq, i) {
            return seq.map(function (value, j) {
                return [i, j, value]
            })
        })

        const height = widget.displayOptions.height || 400
        chartingEngine.heatmapChart({
            title: widget.title,
            chartElementId: elementId,
            xCategories,
            yCategories,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: [].concat.apply([], data),
            min: widget.min,
            max: widget.max,
            twoColors: widget.twoColors,
            height
        })
    };

    _htmlWidget(elementId, widget) {
        $('#' + elementId).html(widget.content)
    }

    genericWidget(widget, filterElement) {
        const widgetId = this._elementId(widget)
        this._genericWidgetForElement(widgetId, widget, filterElement)
    }

    _genericWidgetForElement(widgetId, widget, filterElement) {
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

    _categoricalTableWidget(elementId, widget) {
        var allCategories = widget.data.map(function (series) {
            return series[1].map(function (count) {
                return count.value
            })
        });
        var categories = removeDuplicates([].concat.apply([], allCategories))

        var groups = widget.data.map(function (series) {
            return shorten(series[0], 15)
        });
        var fieldLabel = shorten(widget.fieldLabel, 15)

        var dataMap = widget.data.map(function (series) {
            var map = {}
            $.each(series[1], function (index, count) {
                map[count.value] = count.count
            })
            return map
        });

        var rowData = categories.map(function (categoryName) {
            var sum = 0;
            var data = dataMap.map(function (map) {
                var count = map[categoryName] || 0
                sum += count
                return count
            })
            var result = [categoryName].concat(data)
            if (groups.length > 1) {
                result.push(sum)
            }
            return result
        })

        if (categories.length > 1) {
            var counts = widget.data.map(function (series) {
                var sum = 0
                $.each(series[1], function (index, count) {
                    sum += count.count
                })
                return sum
            });

            var totalCount = counts.reduce(function (a, b) {
                return a + b
            })

            var countRow = ["<b>Total</b>"].concat(counts)
            if (groups.length > 1) {
                countRow.push(totalCount)
            }
            rowData = rowData.concat([countRow])
        }

        var columnNames = [fieldLabel].concat(groups)
        if (groups.length > 1) {
            columnNames.push("Total")
        }

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = $("<div style='position: relative; overflow: auto; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

        var table = createTable(columnNames, rowData)

        div.append(caption)
        div.append(table)

        $('#' + elementId).html(div)
    }

    _numericalTableWidget(elementId, widget) {
        var isDate = widget.fieldType == "Date"

        var groups = widget.data.map(function (series) {
            return shorten(series[0], 15)
        });
        var fieldLabel = shorten(widget.fieldLabel, 15)
        var valueLength = widget.data[0][1].length

        var rowData = Array.from(Array(valueLength).keys()).map(function (index) {
            var row = widget.data.map(function (series) {
                var item = series[1]
                var value = item[index].value
                if (isDate) {
                    value = new Date(value).toISOString()
                }
                return [value, item[index].count]
            })
            return [].concat.apply([], row)
        })

        var columnNames = groups.map(function (group) {
            return [fieldLabel, group]
        })
        var columnNames = [].concat.apply([], columnNames)

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = $("<div style='position: relative; overflow: auto; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

        var table = createTable(columnNames, rowData)

        div.append(caption)

        var centerWrapper = $("<table align='center'>")
        var tr = $("<tr class='vertical-divider' valign='top'>")
        var td = $("<td>")

        td.append(table)
        tr.append(td)
        centerWrapper.append(tr)
        div.append(centerWrapper)

        $('#' + elementId).html(div)
    }

    _categoricalChecboxCountWidget(elementId, widget, filterElement) {
        var widgetElement = $('#' + elementId)

        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = $("<div style='position: relative; overflow: auto; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

        var jumbotron = $("<div class='alert alert-very-light' role='alert'>")

        var rowData = widget.data.map(function (checkCount) {
            var checked = checkCount[0]
            var count = checkCount[1]

            var checkedAttr = checked ? " checked" : ""

            var key = count.key

            var checkbox = '<input type="checkbox" data-key="' + key + '"' + checkedAttr + '/>';

            if (!key) {
                checkbox = ""
            }

            var value = count.value;
            if (checked) {
                value = '<b>' + value + '</b>';
            }
            var count = (checked || !key) ? '(' + count.count + ')' : '---'
            return [checkbox, value, count]
        })

        var checkboxTable = createTable(null, rowData, true)

        jumbotron.append(checkboxTable)

        div.append(caption)
        div.append(jumbotron)

        widgetElement.html(div)

        // add a filter support

        function findCheckedKeys() {
            var keys = []
            $.each(widgetElement.find('input:checkbox'), function () {
                if ($(this).is(":checked")) {
                    keys.push($(this).attr("data-key"))
                }
            });
            return keys;
        }

        widgetElement.find('input:checkbox').on('change', function () {
            var selectedKeys = findCheckedKeys();

            if (selectedKeys.length > 0) {
                var condition = {fieldName: widget.fieldName, conditionType: "in", value: selectedKeys}

                $(filterElement).multiFilter('replaceWithConditionAndSubmit', condition);
            } else
                showError("At least one checkbox must be selected in the widget '" + widget.title + "'.")
        });
    }

    _basicStatsWidget(elementId, widget) {
        var caption = "<h4 align='center'>" + widget.title + "</h4>"
        var columnNames = ["Stats", "Value"]

        function roundOrInt(value) {
            return Number.isInteger(value) ? value : value.toFixed(3)
        }

        var data = [
            ["Min", roundOrInt(widget.data.min)],
            ["Max", roundOrInt(widget.data.max)],
            ["Sum", roundOrInt(widget.data.sum)],
            ["Mean", roundOrInt(widget.data.mean)],
            ["Variance", roundOrInt(widget.data.variance)],
            ["STD", roundOrInt(widget.data.standardDeviation)],
            ["# Defined", widget.data.definedCount],
            ["# Undefined", widget.data.undefinedCount]
        ]

        var height = widget.displayOptions.height || 400
        var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

        var table = createTable(columnNames, data)

        div.append(caption)

        var centerWrapper = $("<table align='center'>")
        var tr = $("<tr class='vertical-divider' valign='top'>")
        var td = $("<td>")

        td.append(table)
        tr.append(td)
        centerWrapper.append(tr)
        div.append(centerWrapper)

        $('#' + elementId).html(div)
    }

    _independenceTestWidget(elementId, widget) {
        var caption = "<h4 align='center'>" + widget.title + "</h4>"

        var height = widget.displayOptions.height || 400
        var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

        var table = createIndependenceTestTable(widget.data)

        div.append(caption)

        var centerWrapper = $("<table align='center'>")
        var tr = $("<tr class='vertical-divider' valign='top'>")
        var td = $("<td>")

        td.append(table)
        tr.append(td)
        centerWrapper.append(tr)
        div.append(centerWrapper)

        $('#' + elementId).html(div)
    }

    widgetDiv(widget, defaultGridWidth, enforceWidth) {
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

    _numericalPointFormat(isDate, isDouble, that, xFloatingPoints, yFloatingPoints) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) +
            this._getPointFormatY(that, yFloatingPoints)
    }

    _numericalCountPointFormat(isDate, isDouble, totalCounts, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            this._getPointFormatY(that) +
            this._getPointFormatPercent(that, totalCounts);
    }

    _categoricalCountPointFormat(totalCounts, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatCategoricalValue(that) +
            this._getPointFormatY(that) +
            this._getPointFormatPercent(that, totalCounts)
    }

    _numericalPercentPointFormat(isDate, isDouble, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            this._getPointFormatPercent2(that);
    }

    _categoricalPercentPointFormat(that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatCategoricalValue(that) +
            this._getPointFormatPercent2(that)
    }

    _getPointFormatHeader(that) {
        var seriesCount = that.series.chart.series.length
        return (seriesCount > 1) ? '<span style="font-size:11px">' + that.series.name + '</span><br>' : ''
    }

    _getPointFormatPercent(that, totalCounts) {
        return ' (<b>' + Highcharts.numberFormat(100 * that.y / totalCounts[that.series.index], 1) + '%</b>)'
    }

    _getPointFormatPercent2(that) {
        return ': <b>' + Highcharts.numberFormat(that.y, 1) + '%</b>'
    }

    _getPointFormatY(that, yFloatingPoints) {
        var yValue = (yFloatingPoints) ? Highcharts.numberFormat(that.y, yFloatingPoints) : that.y
        return ': <b>' + yValue + '</b>'
    }

    _getPointFormatNumericalValue(isDate, isDouble, that, xFloatingPoints) {
        var colorStartPart = '<span style="color:' + that.point.color + '">'

        Highcharts.setOptions({global: {useUTC: false}});

        var valuePart =
            (isDate) ?
                Highcharts.dateFormat('%Y-%m-%d', that.point.x)
                :
                (isDouble) ?
                    ((xFloatingPoints) ? Highcharts.numberFormat(that.point.x, xFloatingPoints) : that.point.x)
                    :
                    that.point.x;

        return colorStartPart + valuePart + '</span>'
    }

    _getPointFormatCategoricalValue(that) {
        return '<span style="color:' + that.point.color + '">' + that.point.name + '</span>'
    }
}
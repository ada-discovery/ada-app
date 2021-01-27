class HighchartsWidgetEngine {

    // Main function of this class
    // TODO: rename to plot
    plot(widget, filterElement) {
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

    // creates a div for a widget
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

    // TODO: rename to refresh
    refreshHighcharts() {
        Highcharts.charts.forEach(function (chart) {
            if (chart) chart.reflow();
        });
    }

    _categoricalCountWidget(elementId, widget, filterElement) {
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
            that._categoricalWidgetAux({
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

    _categoricalWidgetAux({
        chartType,
        categories,
        datas,
        seriesSize,
        title,
        yAxisCaption,
        chartElementId,
        showLabels,
        showLegend,
        height,
        pointFormat
    }) {
        var showLegendExp = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map(function (data, index) {
                    const size = (100 / seriesSize) * (index + 1)
                    var innerSize = 0
                    if (index > 0)
                        innerSize = (100 / seriesSize) * index + 1
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                this._pieChart({
                    title,
                    chartElementId,
                    series,
                    showLabels,
                    showLegend,
                    pointFormat,
                    height,
                    allowSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Column':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    inverted: false,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLabels: true,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Bar':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    inverted: true,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLabels: true,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Line':
                var series = datas

                this._lineChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLegend: showLegendExp,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: null,
                    yDataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Spline':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                this._lineChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLegend: showLegendExp,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: null,
                    yDataType: null,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Polar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                this._polarChart({
                    title,
                    chartElementId,
                    categories,
                    series,
                    showLegend: showLegendExp,
                    pointFormat,
                    height,
                    dataType: null,
                    allowSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
        }
    }

    _numericalCountWidget(elementId, widget, filterElement) {
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
            that._numericalWidgetAux({
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

    _numericalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        xAxisCaption,
        yAxisCaption,
        chartElementId,
        height,
        pointFormat,
        dataType
    }) {
        var showLegend = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map(function (data, index) {
                    const size = (100 / seriesSize) * index
                    const innerSize = Math.max(0, (100 / seriesSize) * (index - 1) + 1)
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                this._pieChart({
                    title,
                    chartElementId,
                    series,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    allowSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
            case 'Column':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    inverted: false,
                    xAxisCaption,
                    yAxisCaption,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Bar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                this._columnChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    inverted: true,
                    xAxisCaption,
                    yAxisCaption,
                    showLabels: false,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Line':
                var series = datas

                this._lineChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    xAxisCaption,
                    yAxisCaption,
                    showLegend,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: dataType,
                    yDataType: null,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Spline':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'spline'};
                });

                this._lineChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    xAxisCaption,
                    yAxisCaption,
                    showLegend,
                    enableDataLabels: true,
                    pointFormat,
                    height,
                    xDataType: dataType,
                    yDataType: null,
                    allowPointSelectionEvent: false,
                    allowIntervalSelectionEvent: true,
                    allowChartTypeChange: true
                });
                break;
            case 'Polar':
                var series = datas.map(function (data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                this._polarChart({
                    title,
                    chartElementId,
                    categories: null,
                    series,
                    showLegend,
                    pointFormat,
                    height,
                    dataType,
                    allowSelectionEvent: false,
                    allowChartTypeChange: true
                });
                break;
        }
    }

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

    _lineWidget(elementId, widget, filterElement) {
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

        this._lineChart({
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

        this._boxPlot({
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

        this._scatterChart({
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

        this._scatterChart({
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
        const xCategories = widget.xCategories
        const yCategories = widget.yCategories
        const data = widget.data.map(function (seq, i) {
            return seq.map(function (value, j) {
                return [i, j, value]
            })
        })

        const height = widget.displayOptions.height || 400

        this._heatmapChart({
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

    _pieChart({
        title,
        chartElementId,
        series,
        showLabels,
        showLegend,
        pointFormat,
        height,
        allowSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                plotBackgroundColor: null,
                plotBorderWidth: null,
                plotShadow: false,
                type: 'pie',
                height: height
            },
            title: {
                text: title
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            plotOptions: {
                pie: {
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    dataLabels: {
                        enabled: showLabels,
                        format: '<b>{point.name}</b>: {point.percentage:.1f}%',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    },
                    showInLegend: showLegend,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    }
                },
                series: {
                    animation: {
                        duration: 400
                    }
                }
            },
            legend: {
                maxHeight: 70
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            series: series
        });
    }

    _columnChart({
        title,
        chartElementId,
        categories,
        series,
        inverted,
        xAxisCaption,
        yAxisCaption,
        showLabels,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null

        var chartType = 'column'
        if (inverted)
            chartType = 'bar'
        $('#' + chartElementId).highcharts({
            chart: {
                type: chartType,
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: 'x'
            },
            title: {
                text: title
            },
            xAxis: {
//                type: 'category',
                type: dataType,
                title: {
                    text: xAxisCaption
                },
                categories: categories,
                crosshair: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                }
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    borderWidth: 0,
                    pointPadding: 0.07,
                    groupPadding: 0.05,
                    dataLabels: {
                        enabled: showLabels,
                        formatter: function () {
                            var value = this.point.y
                            return (value === parseInt(value, 10)) ? value : Highcharts.numberFormat(value, 1)
                        }
                    },
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    _lineChart({
        title,
        chartElementId,
        categories,
        series,
        xAxisCaption,
        yAxisCaption,
        showLegend,
        enableDataLabels,
        pointFormat,
        height,
        xDataType,
        yDataType,
        allowPointSelectionEvent,
        allowIntervalSelectionEvent,
        allowChartTypeChange,
        xMin,
        xMax,
        yMin,
        yMax
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointSelectionEvent || allowIntervalSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        function selectXAxisPointsByDrag(event) {
            if (event.xAxis) {
                var xMin = event.xAxis[0].min;
                var xMax = event.xAxis[0].max;

                $('#' + chartElementId).trigger("intervalSelected", {xMin: xMin, xMax: xMax});
            }
        }

        var selectHandler = (allowIntervalSelectionEvent) ? selectXAxisPointsByDrag : null
        var zoomType = (allowIntervalSelectionEvent) ? 'x' : null

        $('#' + chartElementId).highcharts({
            chart: {
                height: height,
                events: {
                    selection: selectHandler
                },
                zoomType: zoomType
            },
            title: {
                text: title
            },
            xAxis: {
                type: xDataType,
                title: {
                    text: xAxisCaption
                },
                min: xMin,
                max: xMax,
                categories: categories
            },
            yAxis: {
                type: yDataType,
                title: {
                    text: yAxisCaption
                },
                min: yMin,
                max: yMax
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    marker: {
                        enabled: enableDataLabels,
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true
                            }
                        }
                    },
                    turboThreshold: 5000,
                    allowPointSelect: allowPointSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },

            series: series
        })
    }

    _polarChart({
        title,
        chartElementId,
        categories,
        series,
        showLegend,
        pointFormat,
        height,
        dataType,
        allowSelectionEvent,
        allowChartTypeChange
    }) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Spline", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = this._chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowSelectionEvent)
            cursor = 'pointer';

        var pointFormatter = null
        if (pointFormat && (typeof pointFormat === "function")) {
            pointFormatter = pointFormat
            pointFormat = null
        }

        $('#' + chartElementId).highcharts({
            chart: {
                polar: true,
                height: height
            },

            title: {
                text: title
            },
            pane: {
                center: ['50%', '52%'],
                size: '90%',
                startAngle: 0,
                endAngle: 360
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            xAxis: {
                type: dataType,
                categories: categories,
                tickmarkPlacement: 'on',
                lineWidth: 0
            },

            yAxis: {
                gridLineInterpolation: 'polygon',
                labels: {
                    enabled: true
                }
            },
            plotOptions: {
                series: {
                    dataLabels: {
                        enabled: false
                    },
                    allowPointSelect: allowSelectionEvent,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowSelectionEvent)
                                    $('#' + chartElementId).trigger("pointSelected", this);
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    }
                }
            },
            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            series: series
        });
    }

    _scatterChart({
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        showLegend,
        pointFormat,
        height,
        xDataType,
        yDataType,
        zoomIfDragged,
        allowSelectionEvent
    }) {
        var pointFormatter = null
        if (!pointFormat) {
            pointFormatter = this._defaultScatterPointFormatter(xDataType, yDataType)
        } else if (typeof pointFormat === "function") {
            pointFormatter = pointFormat
            pointFormat = null
        }

        const that = this

        /**
         * Custom selection handler that selects points and cancels the default zoom behaviour
         */
        function selectPointsByDrag(e) {

            // Select points
            Highcharts.each(this.series, function (series) {
                Highcharts.each(series.points, function (point) {
                    if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                        point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                        point.select(true, true);
                    }
                });
            });

            that._toast(this, '<b>' + this.getSelectedPoints().length + ' points selected.</b>' +
                '<br>Click on empty space to deselect.');

            if (allowSelectionEvent) triggerSelectionEvent(this.series, e)

            return false; // Don't zoom
        }

        function zoomByDrag(e) {
            if (e.xAxis && e.yAxis) {
                // count the selected points
                var count = 0

                Highcharts.each(this.series, function (series) {
                    Highcharts.each(series.points, function (point) {
                        if (point.x >= e.xAxis[0].min && point.x <= e.xAxis[0].max &&
                            point.y >= e.yAxis[0].min && point.y <= e.yAxis[0].max) {
                            count++;
                        }
                    });
                });

//                toast(this, '<b>' + count + ' points selected.</b>');

                if (allowSelectionEvent) triggerSelectionEvent(this.series, e)
            }

            return true;
        }

        function triggerSelectionEvent(series, event) {
            var cornerPoints = that._findCornerPoints(series, event)
            if (cornerPoints)
                $('#' + chartElementId).trigger("areaSelected", cornerPoints);
        }

        /**
         * On click, unselect all points
         */
        function unselectByClick() {
            var points = this.getSelectedPoints();
            if (points.length > 0) {
                Highcharts.each(points, function (point) {
                    point.select(false);
                });
            }
        }

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'scatter',
                zoomType: 'xy',
                events: {
                    selection: (zoomIfDragged) ? zoomByDrag : selectPointsByDrag,
                    click: unselectByClick
                },
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                title: {
                    enabled: true,
                    text: xAxisCaption
                },
                type: xDataType,
                startOnTick: true,
                endOnTick: true,
                showLastLabel: true
            },
            yAxis: {
                title: {
                    text: yAxisCaption
                },
                type: yDataType
            },
            legend: {
                layout: 'vertical',
                align: 'right',
                verticalAlign: 'middle',
                borderWidth: 0,
                itemStyle: {
                    width: '100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                },
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            plotOptions: {
                scatter: {
                    marker: {
                        radius: 4,
                        states: {
                            hover: {
                                enabled: true,
                                lineColor: 'rgb(100,100,100)'
                            }
                        }
                    },
                    states: {
                        hover: {
                            marker: {
                                enabled: false
                            }
                        }
                    },
                    animation: {
                        duration: 400
                    },
                    turboThreshold: 5000
                }
            },
            tooltip: {
                pointFormat: pointFormat,
                formatter: pointFormatter
            },
            series: series
        });
    }

    _defaultScatterPointFormatter(xDataType, yDataType) {
        Highcharts.setOptions({global: {useUTC: false}});

        var formatter = function () {
            var xPoint = (xDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.x) : this.point.x
            var yPoint = (yDataType == "datetime") ? Highcharts.dateFormat('%Y-%m-%d', this.point.y) : this.point.y

            return '<b>' + this.series.name + '</b><br>' + xPoint + ", " + yPoint
        }
        return formatter;
    }

    _heatmapChart({
        title,
        chartElementId,
        xCategories,
        yCategories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        twoColors,
        height
    }) {
        var colors = (twoColors) ?
            [
                [0, Highcharts.getOptions().colors[5]],
                [0.5, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ] : [
                [0, '#FFFFFF'],
                [1, Highcharts.getOptions().colors[0]]
            ]

        $('#' + chartElementId).highcharts({
            chart: {
                type: 'heatmap',
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                categories: xCategories,
                title: {
                    enabled: (xAxisCaption != null),
                    text: xAxisCaption
                },
                labels: {
                    formatter: function () {
                        return shorten(this.value, 10);
                    }
                }
            },
            yAxis: {
                categories: yCategories,
                title: {
                    enabled: (yAxisCaption != null),
                    text: yAxisCaption
                },
                labels: {
                    formatter: function () {
                        return shorten(this.value, 10);
                    }
                }
            },
            credits: {
                enabled: false
            },
            colorAxis: {
                stops: colors,
                min: min,
                max: max
//                    minColor: '#FFFFFF',
//                    maxColor: Highcharts.getOptions().colors[0]
            },
            legend: {
                align: 'right',
                layout: 'vertical',
                margin: 0,
                verticalAlign: 'top',
                y: 25,
                symbolHeight: 280
            },
            plotOptions: {
                series: {
                    boostThreshold: 100
                }
            },
            boost: {
                useGPUTranslations: true
            },
            tooltip: {
                formatter: function () {
                    var value = (this.point.value != null) ? Highcharts.numberFormat(this.point.value, 3, '.') : "Undefined"
                    return '<b>' + this.series.xAxis.categories[this.point.x] + '</b><br><b>' +
                        this.series.yAxis.categories[this.point.y] + '</b><br>' + value;
                },
                valueDecimals: 2
            },
            series: [{
                name: title,
                borderWidth: 1,
                data: data,
                dataLabels: {
                    enabled: false,
                    color: '#000000'
                }
            }]
        });
    }

    _boxPlot({
        title,
        chartElementId,
        categories,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        pointFormat,
        height,
        dataType
    }) {
        $('#' + chartElementId).highcharts({
            chart: {
                type: 'boxplot',
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                categories: categories,
                title: {
                    text: xAxisCaption
                }
            },
            yAxis: {
                type: dataType,
                title: {
                    text: yAxisCaption
                },
                min: min,
                max: max
            },
            legend: {
                enabled: false
            },
            credits: {
                enabled: false
            },
            plotOptions: {
                boxplot: {
                    fillColor: '#eeeeee',
                    lineWidth: 2,
                    medianWidth: 3,
                    animation: {
                        duration: 400
                    }
                }
            },
            series: [{
                name: title,
                data: data,
                tooltip: {
                    headerFormat: '<b>{point.series.name}</b><br/>',
                    pointFormat: pointFormat
                }
            }]
        });
    }

    _chartTypeMenu(chartElementId, chartTypes) {
        Highcharts.setOptions({
            lang: {
                chartTypeButtonTitle: "Chart Type"
            }
        });

        return {
            buttons: {
                chartTypeButton: {
                    symbol: 'circle',
                    x: '0%',
                    _titleKey: "chartTypeButtonTitle",
                    menuItems:
                        chartTypes.map(function (name) {
                            return {
                                text: name,
                                onclick: function () {
                                    $('#' + chartElementId).trigger("chartTypeChanged", name);
                                }
                            }
                        })
                }
            }
        };
    }

    /**
     * Display a temporary label on the chart
     */
    _toast(chart, text) {
        if (chart.toast)
            chart.toast = chart.toast.destroy();

        chart.toast = chart.renderer.label(text, 100, 120)
            .attr({
                fill: Highcharts.getOptions().colors[0],
                padding: 10,
                r: 5,
                zIndex: 8
            })
            .css({
                color: '#FFFFFF'
            })
            .add();

        setTimeout(function () {
            if (chart.toast)
                chart.toast.fadeOut();
        }, 2000);

        setTimeout(function () {
            if (chart.toast) {
                chart.toast = chart.toast.destroy();
            }
        }, 2500);
    }

    _findCornerPoints(series, event) {
        var xMin, xMax, yMin, yMax;
        Highcharts.each(series, function (series) {
            Highcharts.each(series.points, function (point) {
                if (point.x >= event.xAxis[0].min && point.x <= event.xAxis[0].max &&
                    point.y >= event.yAxis[0].min && point.y <= event.yAxis[0].max) {

                    if (!xMin || point.x < xMin)
                        xMin = point.x

                    if (!xMax || point.x > xMax)
                        xMax = point.x

                    if (!yMin || point.y < yMin)
                        yMin = point.y

                    if (!yMax || point.y > yMax)
                        yMax = point.y
                }
            });
        });
        return (xMin) ? {xMin: xMin, xMax: xMax, yMin: yMin, yMax: yMax} : null
    }

    // Formatters

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
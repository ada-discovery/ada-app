class HighchartsWidgetEngine extends HighchartsWidgetEnginex {

    _fontFamily = 'Helvetica'

    _font = {
        family: this._fontFamily,
        size: 12,
        color: '#666666'
    }

    _tickFont = {
        family: this._fontFamily,
        size: 11,
        color: '#666666'
    }

    _margin ={
        l: 40,
        r: 40,
        t: 40,
        b: 40
    }

    _lineCoreSymbols =
        ["circle", "diamond", "star", "triangle-up", "square-x", "cross", "hexagram", "bowtie", "star-square", "hourglass"]

    _lineOpenSymbols =
        this._lineCoreSymbols.map(function(symbol) {return symbol + "-open"})

    _lineDotSymbols =
        this._lineCoreSymbols.map(function(symbol) {return symbol + "-dot"})

    _lineSymbols =
        this._lineCoreSymbols.concat(this._lineOpenSymbols).concat(this._lineDotSymbols)

    _lineSymbolsCount = this._lineSymbols.length

    _catPalette = ["rgb(124,181,236)",
        "rgb(67,67,72)",
        "rgb(144,237,125)",
        "rgb(247,163,92)",
        "rgb(128,133,233)",
        "rgb(241,92,128)",
        "rgb(228,211,84)",
        "rgb(43,144,143)",
        "#f45b5b",
        "rgb(145,232,225)"
    ]

    _catPaletteSize = this._catPalette.length

    // impl
    _scatterWidget(elementId, widget, filterElement) {
        const that = this

        const series = widget.data.map(function (series, index) {
            return {
                x: series[1].map(function (pairs) { return pairs[0] }),
                y: series[1].map(function (pairs) { return pairs[1] }),
                mode: 'markers',
                type: 'scatter',
                name: shorten(series[0]),
                marker: {
                    size: 6,
                    symbol: that._lineSymbols[index % that._lineSymbolsCount]
                },
                textfont: {
                    family: that._fontFamily
                }
            }
        })

        const height = widget.displayOptions.height || 400;

        const isXDate = widget.xFieldType == "Date"
        const isXDouble = widget.xFieldType == "Double"
        const xDataType = (isXDate) ? 'date' : null;

        const isYDate = widget.yFieldType == "Date"
        const isYDouble = widget.yFieldType == "Double"
        const yDataType = (isYDate) ? 'date' : null;

        this._scatterChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            series,
            showLegend: true,
            height,
            xDataType,
            yDataType
        })

        if (filterElement) {
            this._addScatterAreaZoomed(elementId, filterElement, widget, isXDouble, isXDate, isYDouble, isYDate);
        }
    }

    _scatterChart({
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        showLegend,
        height,
        xDataType,
        yDataType
    }) {
        const layout = this._layout({
            title,
            xAxisCaption,
            xDataType,
            xShowLine: true,
            xShowTicks: true,
            yAxisCaption,
            yDataType,
            yShowGrid: true,
            showLegend,
            height
        })

        Plotly.newPlot(chartElementId, series, layout)

        this._addAreaSelected(chartElementId)
    }

    _addScatterAreaZoomed(elementId, filterElement, widget, isXDouble, isXDate, isYDouble, isYDate) {
        $("#" + elementId).get(0).on('plotly_relayout', function(eventData) {
            const xMin = eventData["xaxis.range[0]"]
            const xMax = eventData["xaxis.range[1]"]
            const yMin = eventData["yaxis.range[0]"]
            const yMax = eventData["yaxis.range[1]"]

            if (xMin) {
                const xMinOut = asTypedStringValue(xMin, isXDouble, isXDate, true)
                const xMaxOut = asTypedStringValue(xMax, isXDouble, isXDate, false)
                const yMinOut = asTypedStringValue(yMin, isYDouble, isYDate, true)
                const yMaxOut = asTypedStringValue(yMax, isYDouble, isYDate, false)

                const conditions = [
                    {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                    {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                    {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                ]

                $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
            }
        });
    }

    _addAreaSelected(elementId) {
        const element = $("#" + elementId).get(0)

        element.on('plotly_selected', function(eventData) {
            if (element.layout.annotations) {
                Plotly.relayout(elementId, 'annotations[0]', 'remove');
            }

            if (eventData) {
                const count = eventData.points.length

                const xMin = element.layout.xaxis.range[0]
                const yMin = element.layout.yaxis.range[0]
                const xMax = element.layout.xaxis.range[1]
                const yMax = element.layout.yaxis.range[1]

                const newAnnotation = {
                    x: xMax - 0.1 * (xMax - xMin),
                    y: yMax - 0.1 * (yMax - yMin),
                    showarrow: false,
                    bgcolor: 'rgba(255, 255, 255, 0.9)',
                    font: {size: 12},
                    borderwidth: 2,
                    borderpad: 4,
                    bordercolor: 'dimGrey',
                    text: '<i># points selected:</i><b>' + count + '</b>'
                }

                Plotly.relayout(elementId, 'annotations[0]', newAnnotation);
            }
        })
    }

    // impl
    _boxWidget(elementId, widget) {
        const isDate = widget.fieldType == "Date"
        const dataType = (isDate) ? 'date' : null;

        const datas = widget.data.map(function (namedQuartiles) {
            const quartiles = namedQuartiles[1]
            return {
                y: [quartiles.lowerWhisker, quartiles.lowerQuantile, quartiles.lowerQuantile, quartiles.median, quartiles.upperQuantile, quartiles.upperQuantile, quartiles.upperWhisker],
                type: "box",
                name: namedQuartiles[0]
            }
        })

        const height = widget.displayOptions.height || 400

        this._boxPlot({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: datas,
            min: widget.min,
            max: widget.max,
            showLegend: false,
            height,
            dataType
        })
    }

    _boxPlot({
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        data,
        min,
        max,
        showLegend,
        height,
        dataType
    }) {
        const yRange = (min && max) ? [min, max] : null

        const layout = this._layout({
            title,
            xAxisCaption,
            xShowLine: true,
            xShowTicks: true,
            yAxisCaption,
            yDataType: dataType,
            yRange,
            yShowGrid: true,
            showLegend,
            height
        })

        Plotly.newPlot(chartElementId, data, layout)
    }

    // impl
    _lineWidget(elementId, widget, filterElement) {
        const isXDate = widget.xFieldType == "Date"
        const isXDouble = widget.xFieldType == "Double"
        const xDataType = (isXDate) ? 'date' : null;

        const isYDate = widget.yFieldType == "Date"
        const yDataType = (isYDate) ? 'date' : null;

        const that = this

        const datas = widget.data.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                x: nameSeries[1].map(function (pairs) { return pairs[0] }),
                y: nameSeries[1].map(function (pairs) { return pairs[1] }),
                mode: 'lines+markers',
                type: 'scatter',
                marker: {
                    size: 6,
                    symbol: that._lineSymbols[index % that._lineSymbolsCount]
                },
                textfont: {
                    family:  that._fontFamily
                }
            }
        })

        const height = widget.displayOptions.height || 400

        const showLegend = datas.length > 1

        this._lineChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            data: datas,
            xMin: widget.xMin,
            xMax: widget.xMax,
            yMin: widget.yMin,
            yMax: widget.yMax,
            showLegend: showLegend,
            height,
            xDataType,
            yDataType
        })

        if (filterElement) {
            this._addXAxisZoomed(elementId, filterElement, widget.xFieldName, isXDouble, isXDate)
        }
    }

    _lineChart({
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        data,
        xMin,
        xMax,
        yMin,
        yMax,
        showLegend,
        height,
        xDataType,
        yDataType
    }) {
        const xRange = (xMin && xMax) ? [xMin, xMax] : null
        const yRange = (yMin && yMax) ? [yMin, yMax] : null

        const layout = this._layout({
            title,
            xAxisCaption,
            xDataType,
            xRange,
            xShowLine: true,
            xShowTicks: true,
            yAxisCaption,
            yDataType,
            yRange,
            yShowGrid: true,
            showLegend,
            height
        })

        Plotly.newPlot(chartElementId, data, layout)

        this._addAreaSelected(chartElementId)
    }

    _categoricalCountWidget(elementId, widget, filterElement) {
        const that = this

        const datas = widget.data.map(function (nameSeries) {
            const name = nameSeries[0]
            const series = nameSeries[1]

            const sum = that._agg(series, widget)
            const data = series.map(function (item) {
                const label = shorten(item.value)
                const count = item.count
                const key = item.key

                const percent = 100 * count / sum
                const value = (widget.useRelativeValues) ? percent : count
                const displayPercent = percent.toFixed(1) + "%"
                const text = (widget.useRelativeValues) ? displayPercent : count + " (" + displayPercent + ")"
                return {x: label, y: value, text: text, key: key}
            })

            return [name, data]
        })

        const seriesSize = datas.length
        const height = widget.displayOptions.height || 400

        const yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._categoricalWidgetAux({
                chartType,
                datas,
                seriesSize,
                title: widget.title,
                yAxisCaption,
                chartElementId: elementId,
                showLabels: widget.showLabels,
                showLegend: widget.showLegend,
                height,
                useRelativeValues: widget.useRelativeValues
            })
        }


        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType);
        })

        plot(widget.displayOptions.chartType)
    }

    _categoricalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        yAxisCaption,
        chartElementId,
        showLabels,
        showLegend,
        height,
        useRelativeValues
    }) {
        var showLegendExp = seriesSize > 1
        const that = this

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

                var textTemplate = (useRelativeValues) ? "<b>%{y:.1f}</b>" : "<b>%{y}</b>"
                var hoverTemplate = that._categoricalXAndTextPointFormat(seriesSize)

                var series = datas.map(function (nameSeries, index) {
                    const size = nameSeries[1].length
                    const palette = Array(Math.ceil(size / that._catPaletteSize)).fill(that._catPalette).flat()

                    return {
                        name: nameSeries[0],
                        x: nameSeries[1].map(function (entry) { return entry.x }),
                        y: nameSeries[1].map(function (entry) { return entry.y }),
                        customdata: nameSeries[1].map(function (entry) { return entry.key }),
                        text: nameSeries[1].map(function (entry) { return entry.text }),
                        meta: [nameSeries[0]], // name
                        texttemplate: textTemplate,
                        hovertemplate: hoverTemplate,
                        type: 'bar',
                        textposition: 'outside',
                        cliponaxis: false,
                        marker: {
                            color: (colorByPoint) ? palette : that._catPalette[index % that._catPaletteSize],
                        },
                        textfont: {
                            size: 11,
                            family:  that._fontFamily
                        }
                    }
                })

                var layout = this._layout({
                    title,
                    xAxisCaption: '',
                    yAxisCaption,
                    xShowLine: true,
                    xShowTicks: true,
                    yShowGrid: true,
                    height,
                    showLegend: showLegendExp,
                    showLabels: true,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                })

                this._chart({
                    chartElementId,
                    data: series,
                    layout
                })

                break;
            case 'Line':
                this._lineChart({
                    title,
                    chartElementId,
                    data: datas,
                    xAxisCaption: '',
                    yAxisCaption,
                    showLegend: showLegendExp,
                    enableDataLabels: true,
                    pointFormat,
                    height,
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

    _chart({
        chartElementId,
        data,
        layout
    }) {
        Plotly.newPlot(chartElementId, data, layout)
    }

    _addXAxisZoomed(elementId, filterElement, fieldName, isDouble, isDate) {
        $("#" + elementId).get(0).on('plotly_relayout', function(eventData) {
            const xMin = eventData["xaxis.range[0]"]
            const xMax = eventData["xaxis.range[1]"]

            if (xMin) {
                const xMinOut = asTypedStringValue(xMin, isDouble, isDate, true)
                const xMaxOut = asTypedStringValue(xMax, isDouble, isDate, false)

                const conditions = [
                    {fieldName: fieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: fieldName, conditionType: "<=", value: xMaxOut}
                ]

                $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
            }
        });
    }

    _layout({
        title,
        xAxisCaption,
        xDataType,
        xRange,
        xShowGrid,
        xShowLine,
        xShowTicks,
        yAxisCaption,
        yDataType,
        yRange,
        yShowGrid,
        yShowLine,
        yShowTicks,
        showLegend,
        height
    }) {
        return {
            margin: this._margin,
            height: height,
            hovermode:'closest',
            xaxis: this._axis({
                title: xAxisCaption,
                dataType: xDataType,
                range: xRange,
                showGrid: xShowGrid,
                showLine: xShowLine,
                showTicks: xShowTicks
            }),
            yaxis: this._axis({
                title: yAxisCaption,
                dataType: yDataType,
                range: yRange,
                showGrid: yShowGrid,
                showLine: yShowLine,
                showTicks: yShowTicks
            }),
            legend: {
                y: 0.5,
                yref: 'paper',
                font: this._font
            },
            bargroupgap: 0.1, // only for bar chart
            title: title,
            showlegend: showLegend
        }
    }

    _axis({
        title,
        dataType,
        range,
        showGrid = false,
        showLine = false,
        showTicks = false
    }) {
        return {
            title: {
                text: title,
                font: this._font,
                standoff: 10
            },
            tickfont: this._tickFont,
            ticklen: (showTicks) ? 8 : null,
            tickcolor: (showTicks )? '#D8D8D8' : null,
            linecolor: '#D8D8D8',
            linewidth: 1,
            type: dataType,
            range: range,
            showgrid: showGrid,
            zeroline: false,
            showline: showLine
        }
    }

    // Formatters

    _categoricalXAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{x}: <b>%{text}</b><extra></extra>'
    }

    _categoricalYAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{y}: <b>%{text}</b><extra></extra>'
    }

    _numericalPercentPointFormat(isDate, isDouble, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatNumericalValue(isDate, isDouble, that, 2) +
            ': <b>%{text}%</b>'
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
            ' (<b>%{text}%</b>)'
    }

    _getPointFormatHeader(seriesCount) {
        return (seriesCount > 1) ? '<span style="font-size:11px">%{meta[0]}</span><br>' : ''
    }

    _getPointFormatY(yFloatingPoints) {
        const yValue = (yFloatingPoints) ? "%{y:." + yFloatingPoints + "f}" : "%{y}"
        return ': <b>' + yValue + '</b>'
    }

    _getPointFormatNumericalValue(isDate, isDouble, xFloatingPoints) {
        const valuePart =
            (isDate) ?
                "%{x}" // TODO: date
                :
                (isDouble) ?
                    ((xFloatingPoints) ? "%{x:." + xFloatingPoints + "f}" : "%{x}")
                    :
                    "%{x}"

        return '<span>' + valuePart + '</span>'
    }
}
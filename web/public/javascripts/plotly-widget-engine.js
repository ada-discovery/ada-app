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
        r: 20,
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

        const height = widget.displayOptions.height || 400

        const showLegend = widget.data.length > 1

        this._lineChart({
            title: widget.title,
            chartElementId: elementId,
            xAxisCaption: widget.xAxisCaption,
            yAxisCaption: widget.yAxisCaption,
            datas: widget.data,
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
        datas,
        xMin,
        xMax,
        yMin,
        yMax,
        showLegend,
        height,
        xDataType,
        yDataType,
        useSpline
    }) {
        const that = this

        const xRange = (xMin && xMax) ? [xMin, xMax] : null
        const yRange = (yMin && yMax) ? [yMin, yMax] : null

        const data = datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                x: nameSeries[1].map(function (pairs) { return pairs[0] }),
                y: nameSeries[1].map(function (pairs) { return pairs[1] }),
                mode: 'lines+markers',
                type: 'scatter',
                line: (useSpline) ? {shape: 'spline'} : {},
                marker: {
                    size: 6,
                    symbol: that._lineSymbols[index % that._lineSymbolsCount]
                },
                textfont: {
                    family:  that._fontFamily
                }
            }
        })

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

            if (filterElement) {
                that._addPointClicked(elementId, filterElement, widget.fieldName)
            }
        }

        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType);
        })

        plot(widget.displayOptions.chartType)

        const chartTypeMenu =
            '<div class="chart-type-menu dropdown" style="position: absolute; left: 15px; top: 0px; z-index: 10">\
                <button class="btn btn-sm dropdown-toggle" style="background-color:transparent" type="button" id="dropdownMenu2" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">\
                    <span class="dot" aria-hidden="true"></span>\
                </button>\
                <ul class="dropdown-menu">\
                    <li><a class="chart-type-menu-item" data-chart-type="Pie" href="#">Pie</a></li>\
                    <li><a class="chart-type-menu-item" data-chart-type="Column" href="#">Column</a></li>\
                    <li><a class="chart-type-menu-item" data-chart-type="Bar" href="#">Bar</a></li>\
                    <li><a class="chart-type-menu-item" data-chart-type="Line" href="#">Line</a></li>\
                    <li><a class="chart-type-menu-item" data-chart-type="Spline" href="#">Spline</a></li>\
                    <li><a class="chart-type-menu-item" data-chart-type="Polar" href="#">Polar</a></li>\
            </ul>\
            </div>';

        $("#" + elementId).prepend(chartTypeMenu)
        $("#" + elementId).find(".chart-type-menu-item").on("click", function(event) {
            const chartType = $(event.target).attr("data-chart-type")
            $('#' + elementId).trigger("chartTypeChanged", chartType)
        })
    }

    _categoricalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        yAxisCaption,
        chartElementId,
        showLabels, // TODO: ignored?
        showLegend, // TODO: ignored?
        height,
        useRelativeValues
    }) {
        const showLegendExp = seriesSize > 1
        const that = this

        var series, layout

        switch (chartType) {
            case 'Pie':
                series = that._pieData(datas, true).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{value:.1f}</b>" : "<b>%{value}</b>"
                    seriesEntry.hovertemplate = that._categoricalLabelAndTextPointFormat(seriesSize)

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    height,
                    showLegend: true
                })

                if (seriesSize > 1) {
                    const xStep = 1 / seriesSize

                    layout.annotations = series.map(function (seriesEntry, index) {
                        return {
                            font: {
                                size: 13,
                                family: that._fontFamily
                            },
                            xanchor: 'center',
                            yanchor: 'center',
                            showarrow: false,
                            text: shorten(seriesEntry.name, 20),
                            x: (xStep / 2) + xStep * index,
                            y: 0.5
                        }
                    })
                }

                break;
            case 'Column':
                series = that._columnData(datas, true, true).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{y:.1f}</b>" : "<b>%{y}</b>"
                    seriesEntry.hovertemplate = that._categoricalXAndTextPointFormat(seriesSize)

                    return seriesEntry
                })

                layout = this._layout({
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

                break;
            case 'Bar':
                series = that._columnData(datas, true, true).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{x:.1f}</b>" : "<b>%{x}</b>"
                    seriesEntry.hovertemplate = that._categoricalYAndTextPointFormat(seriesSize)
                    seriesEntry.orientation = 'h'

                    // swap x and y coordinates
                    const xx = seriesEntry.x
                    seriesEntry.x = seriesEntry.y
                    seriesEntry.y = xx

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    xAxisCaption: yAxisCaption,
                    yAxisCaption: '',
                    yShowLine: true,
                    yShowTicks: true,
                    xShowGrid: true,
                    height,
                    showLegend: showLegendExp,
                    showLabels: true,
                    allowPointSelectionEvent: true,
                    allowIntervalSelectionEvent: false,
                    allowChartTypeChange: true
                })

                break;
            case 'Line':
                series = that._lineData(datas, false).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{y:.1f}</b>" : "<b>%{y}</b>"
                    seriesEntry.hovertemplate = that._categoricalXAndTextPointFormat(seriesSize)

                    return seriesEntry
                })

                layout = this._layout({
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

                break;
            case 'Spline':
                series = that._lineData(datas, false).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{y:.1f}</b>" : "<b>%{y}</b>"
                    seriesEntry.hovertemplate = that._categoricalXAndTextPointFormat(seriesSize)
                    seriesEntry.line = {shape: 'spline'}

                    return seriesEntry
                })

                layout = this._layout({
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

                break;
            case 'Polar':
                series = that._polarData(datas, false).map(function (seriesEntry, index) {
                    seriesEntry.texttemplate = (useRelativeValues) ? "<b>%{r:.1f}</b>" : "<b>%{r}</b>"
                    seriesEntry.hovertemplate = that._categoricalPolarLabelAndTextPointFormat(seriesSize)

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    height,
                    showLegend: showLegendExp
                })

                layout.polar = {
                    radialaxis: {
                        showline: false,
                        tickfont: {
                            size: 9,
                            family:  that._fontFamily
                        },
                        angle: 90
                    },
                    angularaxis: {
                        showline: false,
                        direction: "clockwise",
                        tickfont: that._tickFont
                    }
                }

                break;
        }

        this._chart({
            chartElementId,
            data: series,
            layout
        })
    }

    // impl
    _numericalCountWidget(elementId, widget, filterElement) {
        const that = this

        const datas = widget.data.map(function (nameSeries) {
            const name = nameSeries[0]
            const series = nameSeries[1]

            const sum = that._agg(series, widget)
            const data = series.map(function (item) {
                const count = item.count

                const percent = 100 * count / sum
                const value = (widget.useRelativeValues) ? percent : count
                const displayPercent = percent.toFixed(1) + "%"
                const text = (widget.useRelativeValues) ? displayPercent : count + " (" + displayPercent + ")"
                return {x: item.value, y: value, text: text}
            })

            return [name, data]
        })

        const seriesSize = datas.length
        const height = widget.displayOptions.height || 400

        const yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

        function plot(chartType) {
            that._numericalWidgetAux({
                chartType,
                datas,
                seriesSize,
                title: widget.title,
                xAxisCaption: widget.fieldLabel,
                yAxisCaption,
                chartElementId: elementId,
                showLabels: widget.showLabels,
                showLegend: widget.showLegend,
                height,
                useRelativeValues: widget.useRelativeValues,
                xFieldType: widget.fieldType
            })

            if (filterElement) {
                const isDate = widget.fieldType == "Date"
                const isDouble = widget.fieldType == "Double"
                // TODO: bar chart uses yAxisZoom not the x-one
                that._addXAxisZoomed(elementId, filterElement, widget.fieldName, isDouble, isDate)
            }
        }

        $('#' + elementId).on('chartTypeChanged', function (event, chartType) {
            plot(chartType);
        })

        plot(widget.displayOptions.chartType)
    }

    _numericalWidgetAux({
        chartType,
        datas,
        seriesSize,
        title,
        xAxisCaption,
        yAxisCaption,
        chartElementId,
        showLabels, // TODO: ignored?
        showLegend, // TODO: ignored?
        height,
        useRelativeValues,
        xFieldType
    }) {
        const that = this
        const showLegendExp = seriesSize > 1

        const isDate = xFieldType == "Date"
        const isDouble = xFieldType == "Double"
        const dataType = (isDate) ? 'date' : null;

        var series, layout

        switch (chartType) {
            case 'Pie':
                series = datas.map(function (data, index) {
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
                series = that._columnData(datas, false, false).map(function (seriesEntry, index) {
                    seriesEntry.hovertemplate = that._numericalXAndTextPointFormat(seriesSize, isDate, isDouble)

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    xAxisCaption,
                    yAxisCaption,
                    xShowLine: true,
                    xShowTicks: true,
                    yShowGrid: true,
                    xDataType: dataType,  // extra arg
                    height,
                    showLegend: showLegendExp
                })

                break;
            case 'Bar':
                series = that._columnData(datas, false, false).map(function (seriesEntry, index) {
                    seriesEntry.hovertemplate = that._numericalYAndTextPointFormat(seriesSize, isDate, isDouble)
                    seriesEntry.orientation = 'h'

                    // swap x and y coordinates
                    const xx = seriesEntry.x
                    seriesEntry.x = seriesEntry.y
                    seriesEntry.y = xx

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    xAxisCaption: yAxisCaption,
                    yAxisCaption: xAxisCaption,
                    yShowLine: true,
                    yShowTicks: true,
                    xShowGrid: true,
                    yDataType: dataType,  // extra arg
                    height,
                    showLegend: showLegendExp
                })

                break;
            case 'Line':
                series = that._lineData(datas, false).map(function (seriesEntry, index) {
                    seriesEntry.hovertemplate = that._numericalXAndTextPointFormat(seriesSize, isDate, isDouble)

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    xAxisCaption,
                    yAxisCaption,
                    xShowLine: true,
                    xShowTicks: true,
                    yShowGrid: true,
                    xDataType: dataType, // extra arg
                    height,
                    showLegend: showLegendExp
                })

                break;
            case 'Spline':
                series = that._lineData(datas, false).map(function (seriesEntry, index) {
                    seriesEntry.hovertemplate = that._numericalXAndTextPointFormat(seriesSize, isDate, isDouble)
                    seriesEntry.line = {shape: 'spline'}

                    return seriesEntry
                })

                layout = this._layout({
                    title,
                    xAxisCaption,
                    yAxisCaption,
                    xShowLine: true,
                    xShowTicks: true,
                    yShowGrid: true,
                    xDataType: dataType, // extra arg
                    height,
                    showLegend: showLegendExp
                })

                break;
            case 'Polar':
                series = datas.map(function (data, index) {
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

        this._chart({
            chartElementId,
            data: series,
            layout
        })
    }

    _columnData(datas, showText, colorByPoint) {
        const that = this
        const seriesSize = datas.length
        const colorByPointFinal = colorByPoint && (seriesSize == 1)

        return datas.map(function (nameSeries, index) {
            const size = nameSeries[1].length
            const palette = Array(Math.ceil(size / that._catPaletteSize)).fill(that._catPalette).flat()

            return {
                name: nameSeries[0],
                x: nameSeries[1].map(function (entry) { return entry.x }),
                y: nameSeries[1].map(function (entry) { return entry.y }),
                customdata: nameSeries[1].map(function (entry) { return entry.key }),
                text: nameSeries[1].map(function (entry) { return entry.text }),
                meta: [nameSeries[0]], // name
                type: 'bar',
                textposition: (showText) ? "outside" : "none",
                cliponaxis: false,
                marker: {
                    color: (colorByPointFinal) ? palette : that._catPalette[index % that._catPaletteSize],
                },
                textfont: {
                    size: 11,
                    family:  that._fontFamily
                }
            }
        })
    }

    _lineData(datas, showText) {
        const that = this

        return datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                x: nameSeries[1].map(function (entry) { return entry.x }),
                y: nameSeries[1].map(function (entry) { return entry.y }),
                customdata: nameSeries[1].map(function (entry) { return entry.key }),
                text: nameSeries[1].map(function (entry) { return entry.text }),
                meta: [nameSeries[0]], // name
                type: 'scatter',
                mode: (showText) ? 'lines+markers+text' : 'lines+markers',
                textposition: 'top',
                cliponaxis: false,
                marker: {
                    size: 6,
                    color: that._catPalette[index % that._catPaletteSize],
                    symbol: that._lineSymbols[index % that._lineSymbolsCount]
                },
                textfont: {
                    size: 11,
                    family:  that._fontFamily
                }
            }
        })
    }

    _pieData(datas, showText) {
        const that = this
        const seriesSize = datas.length
        const xStep = 1 / seriesSize

        return datas.map(function (nameSeries, index) {
            const size = nameSeries[1].length
            const palette = Array(Math.ceil(size / that._catPaletteSize)).fill(that._catPalette).flat()

            return {
                name: nameSeries[0],
                labels: nameSeries[1].map(function (entry) { return entry.x }),
                values: nameSeries[1].map(function (entry) { return entry.y }),
                customdata: nameSeries[1].map(function (entry) { return entry.key }),
                text: nameSeries[1].map(function (entry) { return entry.text }),
                meta: [nameSeries[0]], // name
                type: 'pie',
                textposition: (showText) ? "inside" : "none",
                domain:{
                    x: [xStep * index, xStep * (index + 1)],
                    y: [0, 1],
                },
                hole: (seriesSize > 1) ? 0.3 : null,
                cliponaxis: false,
                marker: {
                    colors: palette,
                    line : {
                        color: "white",
                        width: 2
                    }
                },
                textfont: {
                    size: 11,
                    family:  that._fontFamily
                }
            }
        })
    }

    _polarData(datas, showText) {
        const that = this

        return datas.map(function (nameSeries, index) {
            return {
                name: nameSeries[0],
                theta: nameSeries[1].map(function (entry) { return entry.x }),
                r: nameSeries[1].map(function (entry) { return entry.y }),
                customdata: nameSeries[1].map(function (entry) { return entry.key }),
                text: nameSeries[1].map(function (entry) { return entry.text }),
                meta: [nameSeries[0]], // name
                type: 'scatterpolar',
                fill: "toself",
                mode: (showText) ? 'lines+markers+text' : 'lines+markers',
                cliponaxis: false,
                marker: {
                    color: that._catPalette[index % that._catPaletteSize],
                    symbol: that._lineSymbols[index % that._lineSymbolsCount]
                }
            }
        })
    }

    _chart({
        chartElementId,
        data,
        layout
    }) {
        Plotly.newPlot(chartElementId, data, layout)
    }

    _addPointClicked(elementId, filterElement, fieldName) {
        $("#" + elementId).get(0).on('plotly_click', function(eventData) {
            if (eventData.points.length > 0) {
                const key = eventData.points[0].customdata

                const condition = {fieldName: fieldName, conditionType: "=", value: key}

                $(filterElement).multiFilter('replaceWithConditionAndSubmit', condition)
            }
        });
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
                standoff: 12
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
            showline: showLine,
            automargin: true
        }
    }

    // Formatters

    // categorical

    _categoricalXAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{x}: <b>%{text}</b><extra></extra>'
    }

    _categoricalYAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{y}: <b>%{text}</b><extra></extra>'
    }

    _categoricalLabelAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{label}: <b>%{text}</b><extra></extra>'
    }

    _categoricalPolarLabelAndTextPointFormat(seriesCount) {
        return this._getPointFormatHeader(seriesCount) + '%{theta}: <b>%{text}</b><extra></extra>'
    }

    // numerical

    _numericalXAndTextPointFormat(seriesCount, isDate, isDouble) {
        return this._getPointFormatHeader(seriesCount) +
            this._getPointFormatXNumericalValue(isDate, isDouble, 2) +
            ': <b>%{text}</b><extra></extra>'
    }

    _numericalYAndTextPointFormat(seriesCount, isDate, isDouble) {
        return this._getPointFormatHeader(seriesCount) +
            this._getPointFormatYNumericalValue(isDate, isDouble, 2) +
            ': <b>%{text}</b><extra></extra>'
    }

    _numericalCountPointFormat(isDate, isDouble, totalCounts, that) {
        return this._getPointFormatHeader(that) +
            this._getPointFormatXNumericalValue(isDate, isDouble, that, 2) +
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

    _getPointFormatXNumericalValue(isDate, isDouble, floatingPoints) {
        const valuePart =
            (isDate) ?
                "%{x|%Y/%m/%d %H:%M:%S }"
                :
                (isDouble) ?
                    ((floatingPoints) ? "%{x:." + floatingPoints + "f}" : "%{x}")
                    :
                    "%{x}"

        return '<span>' + valuePart + '</span>'
    }

    _getPointFormatYNumericalValue(isDate, isDouble, floatingPoints) {
        const valuePart =
            (isDate) ?
                "%{y|%Y/%m/%d %H:%M:%S }"
                :
                (isDouble) ?
                    ((floatingPoints) ? "%{y:." + floatingPoints + "f}" : "%{y}")
                    :
                    "%{y}"

        return '<span>' + valuePart + '</span>'
    }
}
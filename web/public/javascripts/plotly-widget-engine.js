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

    _lineCoreSymbols =
        ["circle", "diamond", "star", "triangle-up", "square-x", "cross", "hexagram", "bowtie", "star-square", "hourglass"]

    _lineOpenSymbols =
        this._lineCoreSymbols.map(function(symbol) {return symbol + "-open"})

    _lineDotSymbols =
        this._lineCoreSymbols.map(function(symbol) {return symbol + "-dot"})

    _lineSymbols =
        this._lineCoreSymbols.concat(this._lineOpenSymbols).concat(this._lineDotSymbols)

    _lineSymbolsCount = this._lineSymbols.length

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
        const layout = {
            height: height,
            hovermode:'closest',
            xaxis: {
                title: xAxisCaption,
                type: xDataType
            },
            yaxis: {
                title: yAxisCaption,
                type: yDataType
            },
            legend: {
                y: 0.5,
                yref: 'paper',
                font: this._font
            },
            showlegend: showLegend,
            title: title
        };

        Plotly.newPlot(chartElementId, series, layout);

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
        });
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

        const layout = {
            height: height,
            xaxis: {
                title: xAxisCaption
            },
            yaxis: {
                title: yAxisCaption,
                type: dataType,
                range: yRange
            },
            legend: {
                y: 0.5,
                yref: 'paper',
                font: this._font
            },
            title: title,
            showlegend: showLegend
        };

        Plotly.newPlot(chartElementId, data, layout);
    }
}
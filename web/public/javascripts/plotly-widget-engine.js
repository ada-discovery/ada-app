class HighchartsWidgetEngine extends HighchartsWidgetEnginex {

    // impl
    _scatterWidget(elementId, widget, filterElement) {
        const series = widget.data.map(function (series) {
            return {
                x: series[1].map(function (pairs) { return pairs[0] }),
                y: series[1].map(function (pairs) { return pairs[1] }),
                mode: 'markers',
                type: 'scatter',
                name: shorten(series[0]),
                marker: { size: 6 },
                textfont: {
                    family:  'Lucida Grande'
                },
            }
        })

        const height = widget.displayOptions.height || 400;

        const isXDate = widget.xFieldType == "Date"
        const xDataType = (isXDate) ? 'date' : null;

        const isYDate = widget.yFieldType == "Date"
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
            this._addScatterAreaZoomed(elementId, filterElement, widget, isXDate, isYDate);
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
                font: {
                    family: 'Lucida Grande',
                    size: 12,
                    color: 'dimGrey',
                }
            },
            showlegend: showLegend,
            title: title
        };

        Plotly.newPlot(chartElementId, series, layout);

        this._addScatterAreaSelected(chartElementId)
    }

    _addScatterAreaZoomed(elementId, filterElement, widget, isXDate, isYDate) {
        $("#" + elementId).get(0).on('plotly_relayout', function(eventData) {
            const xMin = eventData["xaxis.range[0]"]
            const xMax = eventData["xaxis.range[1]"]
            const yMin = eventData["yaxis.range[0]"]
            const yMax = eventData["yaxis.range[1]"]

            if (xMin) {
                const xMinOut = (isXDate) ? msOrDateToStandardDateString(xMin) : xMin.toString();
                const xMaxOut = (isXDate) ? msOrDateToStandardDateString(xMax) : xMax.toString();
                const yMinOut = (isYDate) ? msOrDateToStandardDateString(yMin) : yMin.toString();
                const yMaxOut = (isYDate) ? msOrDateToStandardDateString(yMax) : yMax.toString();

                var conditions = [
                    {fieldName: widget.xFieldName, conditionType: ">=", value: xMinOut},
                    {fieldName: widget.xFieldName, conditionType: "<=", value: xMaxOut},
                    {fieldName: widget.yFieldName, conditionType: ">=", value: yMinOut},
                    {fieldName: widget.yFieldName, conditionType: "<=", value: yMaxOut}
                ]

                $(filterElement).multiFilter('addConditionsAndSubmit', conditions);
            }
        });
    }

    _addScatterAreaSelected(elementId) {
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
}
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
        var layout = {
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
                },
                showlegend: showLegend
            },
            title: title
        };

        Plotly.newPlot(chartElementId, series, layout);
    }
}
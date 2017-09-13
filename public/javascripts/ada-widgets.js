function elementId(widget) {
    return widget._id.$oid + "Widget"
}

function shorten(string, length) {
    var initLength = length || 25
    return (string.length > initLength) ? string.substring(0, initLength) + ".." : string
}

function agg(series, widget) {
    var counts = series.map(function(item) {
        return item.count;
    });

    if (widget.isCumulative) {
        var max = counts.reduce(function(a, b) {
            return Math.max(a, b);
        });

        return max
    } else {
        var sum = counts.reduce(function(a, b) {
            return a + b;
        });

        return sum
    }
}

function categoricalCountWidget(elementId, widget, filterElementId) {
    var categories = (widget.data.length > 0) ?
        widget.data[0][1].map(function(count) {
            return count.value
        })
        : []

    var datas = widget.data.map(function(nameSeries){
        var name = nameSeries[0]
        var series = nameSeries[1]

        var sum = agg(series, widget)
        var data = series.map(function(item) {
            var label = shorten(item.value)
            var count = item.count
            var key = item.key

            var value = (widget.useRelativeValues) ? 100 * count / sum : count
            return {name: label, y: value, key: key}
        })

        return {name: name, data: data}
    })

    var totalCounts = widget.data.map(function(nameSeries) {
        return agg(nameSeries[1], widget);
    })

    var seriesSize = datas.length
    var height = widget.displayOptions.height || 400
    var pointFormat = function () {
        return (widget.useRelativeValues) ? categoricalPercentPointFormat(this) : categoricalCountPointFormat(totalCounts, this);
    }
    var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

    $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        changeCategoricalCountChartType(chartType, categories, datas, seriesSize, widget.title, yAxisCaption, elementId, widget.showLabels, widget.showLegend, height, pointFormat);
    });
    changeCategoricalCountChartType(widget.displayOptions.chartType, categories, datas, seriesSize, widget.title, yAxisCaption, elementId, widget.showLabels, widget.showLegend, height, pointFormat)

    if (filterElementId) {
        $('#' + elementId).on('pointClick', function (event, data) {
            var condition = {fieldName: widget.fieldName, conditionType: "=", value: data.key};
            $('#' + filterElementId).multiFilter('addAndSubmitCondition', condition);
        });
    }
}

function numericalCountWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"
    var isDouble = widget.fieldType == "Double"
    var dataType = (isDate) ? 'datetime' : null;

    var datas = widget.data.map(function(nameSeries){
        var name = nameSeries[0]
        var series = nameSeries[1]

        var sum = agg(series, widget)
        var data = series.map(function(item) {
            var count = item.count

            var y = (widget.useRelativeValues) ? 100 * count / sum : count
            return [item.value, y]
        })

        return {name: name, data: data}
    })

    var totalCounts = widget.data.map(function(nameSeries) {
        return agg(nameSeries[1], widget);
    })

    var seriesSize = datas.length
    var height = widget.displayOptions.height || 400
    var pointFormat = function () {
        return (widget.useRelativeValues) ? numericalPercentPointFormat(isDate, isDouble, this) : numericalCountPointFormat(isDate, isDouble, totalCounts, this);
    }
    var yAxisCaption = (widget.useRelativeValues) ? '%' : 'Count'

    $('#' + elementId).on('chartTypeChanged', function(event, chartType) {
        changeNumericalCountChartType(chartType, datas, seriesSize, widget.title, yAxisCaption, widget.fieldLabel, elementId, height, pointFormat, dataType)
    });

    changeNumericalCountChartType(widget.displayOptions.chartType, datas, seriesSize, widget.title, yAxisCaption, widget.fieldLabel, elementId, height, pointFormat, dataType)
}

function boxWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"
    var dataType = (isDate) ? 'datetime' : null;

    var data = widget.data
    var datas = [[data.lowerWhisker, data.lowerQuantile, data.median, data.upperQuantile, data.upperWhisker]]

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
    boxPlot(widget.title, elementId, widget.yAxisCaption, datas, min, max, pointFormat, height, dataType)
}

function scatterWidget(elementId, widget) {
    var datas = widget.data.map(function(series) {
        return {name : shorten(series[0]), data : series[2]}
    })

    var height = widget.displayOptions.height || 400;

    scatterChart(widget.title, elementId, widget.xAxisCaption, widget.yAxisCaption, datas, height)
}

function heatmapWidget(elementId, widget) {
    var xCategories =  widget.xCategories
    var yCategories =  widget.yCategories
    var data = widget.data.map(function(seq, i) {
        return seq.map(function(value, j) {
            return [i, j, value || 0]
        })
    })

    var height = widget.displayOptions.height || 400
    heatmapChart(widget.title, elementId, xCategories, yCategories, [].concat.apply([], data), height)
};

function htmlWidget(elementId, widget) {
    $('#' + elementId).html(widget.content)
}

function genericWidget(widget, filterElementId) {
    var elementIdVal = elementId(widget)
    if(widget.displayOptions.isTextualForm)
        switch (widget.concreteClass) {
            case "models.CategoricalCountWidget": categoricalTableWidget(elementIdVal, widget); break;
            case "models.NumericalCountWidget": numericalTableWidget(elementIdVal, widget); break;
            default: console.log(widget.concreteClass + " does not have a textual representation.")
        }
    else
        switch (widget.concreteClass) {
            case "models.CategoricalCountWidget": categoricalCountWidget(elementIdVal, widget, filterElementId); break;
            case "models.NumericalCountWidget": numericalCountWidget(elementIdVal, widget); break;
            case "models.BoxWidget": boxWidget(elementIdVal, widget); break;
            case "models.ScatterWidget": scatterWidget(elementIdVal, widget); break;
            case "models.HeatmapWidget": heatmapWidget(elementIdVal, widget); break;
            case "models.HtmlWidget": htmlWidget(elementIdVal, widget); break;
            default: console.log("Widget type" + widget.concreteClass + " unrecognized.")
        }
}

function categoricalTableWidget(elementId, widget) {
    var allCategories = widget.data.map(function(series){ return series[1].map(function(count){ return count.value })});
    var categories = removeDuplicates([].concat.apply([], allCategories))

    var groups = widget.data.map(function(series){ return shorten(series[0], 15) });
    var fieldLabel = shorten(widget.fieldLabel, 15)

    var dataMap = widget.data.map(function(series){
        var map = {}
        $.each(series[1], function(index, count){
            map[count.value] = count.count
        })
        return map
    });

    var rowData = categories.map(function(categoryName) {
        var sum = 0;
        var data = dataMap.map(function(map) {
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
        var counts = widget.data.map(function(series){
            var sum = 0
            $.each(series[1], function(index, count){
                sum += count.count
            })
            return sum
        });

        var totalCount = counts.reduce(function(a,b) {return a + b })

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
    var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

    var table = createTable(columnNames, rowData)

    div.append(caption)
    div.append(table)

    $('#' + elementId).html(div)
}


function numericalTableWidget(elementId, widget) {
    var isDate = widget.fieldType == "Date"

    var groups = widget.data.map(function(series){ return shorten(series[0], 15) });
    var fieldLabel = shorten(widget.fieldLabel, 15)
    var valueLength = widget.data[0][1].length

    var rowData = Array.from(Array(valueLength).keys()).map(function(index){
        var row = widget.data.map(function(series){
            var item = series[1]
            var value = item[index].value
            if (isDate) {
                value = new Date(value).toISOString()
            }
            return [value, item[index].count]
        })
        return [].concat.apply([], row)
    })

    var columnNames = groups.map(function(group){return [fieldLabel, group]})
    var columnNames = [].concat.apply([], columnNames)

    var caption = "<h4 align='center'>" + widget.title + "</h4>"

    var height = widget.displayOptions.height || 400
    var div = $("<div style='position: relative; overflow: hidden; height:" + height + "px; text-align: left; line-height: normal; z-index: 0;'>")

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

function createTable(columnNames, rows) {
    var table = $("<table class='table table-striped'>")

    // head
    var thead = $("<thead>")
    var theadTr = $("<tr>")

    $.each(columnNames, function(index, columnName) {
        var th = "<th class='col header'>" + columnName + "</th>"
        theadTr.append(th)
    })
    thead.append(theadTr)
    table.append(thead)

    // body
    var tbody = $("<tbody>")

    $.each(rows, function(index, row) {
        var tr = $("<tr>")
        $.each(row, function(index, item) {
            var td = "<td>" + item + "</td>"
            tr.append(td)
        })
        tbody.append(tr)
    })
    table.append(tbody)

    return table
}

function widgetDiv(widget, gridWidth) {
    var elementIdVal = elementId(widget)
    var initGridWidth = widget.displayOptions.gridWidth || gridWidth
    var gridWidthElement = "col-md-" + initGridWidth

    var gridOffset = widget.displayOptions.gridOffset
    var gridOffsetElement = gridOffset ? "col-md-offset-" + gridOffset : ""

    var innerDiv = '<div id="' + elementIdVal + '" class="chart-holder"></div>'
    var div = $("<div class='" + gridWidthElement + " " + gridOffsetElement + "'>")
    div.append(innerDiv)
    return div
}
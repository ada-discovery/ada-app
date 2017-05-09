    function pieChart(
        title,
        chartElementId,
        series,
        showLabels,
        showLegend,
        height,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

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
                pointFormat: 'Count: <b>{point.y}</b>'
            },
            plotOptions: {
                pie: {
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    dataLabels: {
                        enabled: showLabels,
                        format: '<b>{point.x}</b>: {point.percentage:.1f}%',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    },
                    showInLegend: showLegend,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementId).trigger("pointClick", this);
                            }
                        }
                    }
                },
                series: {
                    animation: {
                        duration: 600
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

    function columnChart(
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
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        var chartType = 'column'
        if (inverted)
            chartType = 'bar'
        $('#' + chartElementId).highcharts({
            chart: {
                type: chartType,
                height: height
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
                    width:'100px',
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
                    dataLabels: {
                        enabled: showLabels,
                        format: '{point.y:.0f}'
                    },
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementId).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 600
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat
            },

            series: series
        })
    }

    function timeLineChart(
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        height
    ) {
        $('#' + chartElementId).highcharts({
            chart: {
                type: 'line', // 'spline'
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                type: 'datetime',
                //dateTimeLabelFormats: { // don't display the dummy year
                //    month: '%e. %b',
                //    year: '%b'
                //},
                title: {
                    text: xAxisCaption
                }
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
                    width:'80px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                }
            },
            credits: {
                enabled: false
            },
            tooltip: {
                headerFormat: '<b>{series.name}</b><br>',
                pointFormat: '{point.x:%d.%m.%Y}: {point.y:.0f}'
            },

            plotOptions: {
                line: {
                    marker: {
                        enabled: true
                    }
                }
            },

            series: series
        });
    }

    function lineChart(
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
        dataType,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementId).highcharts({
            chart: {
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
                type: dataType,
                title: {
                    text: xAxisCaption
                },
                categories: categories
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
                    width:'80px',
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
                        states: {
                            hover: {
                                enabled: true
                            }
                        }
                    },
                    turboThreshold: 5000,
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementId).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 600
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: pointFormat
            },

            series: series
        })
    }

    function polarChart(
        title,
        chartElementId,
        categories,
        series,
        showLegend,
        height,
        dataType,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementId, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

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
                    width:'80px',
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
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementId).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 600
                    }
                }
            },
            tooltip: {
                pointFormat: 'Count: <b>{point.y}</b>'
            },
            exporting: exporting,
            credits: {
                enabled: false
            },
            series: series
        });
    }

    function scatterChart(
        title,
        chartElementId,
        xAxisCaption,
        yAxisCaption,
        series,
        height
    ) {
        $('#' + chartElementId).highcharts({
            chart: {
                type: 'scatter',
                zoomType: 'xy',
                height: height
            },
            title: {
                text:  title
            },
            xAxis: {
                title: {
                    enabled: true,
                        text: xAxisCaption
                },
                startOnTick: true,
                endOnTick: true,
                showLastLabel: true
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
                    width:'100px',
                    textOverflow: 'ellipsis',
                    overflow: 'hidden'
                }
            },
            credits: {
                enabled: false
            },
            plotOptions: {
                scatter: {
                    marker: {
                        radius: 5,
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
                    tooltip: {
                        headerFormat: '<b>{series.name}</b><br>',
                            pointFormat: '{point.x}, {point.y}'
                    }
                }
            },
            series: series
        });
    }

    function boxPlot(
        title,
        chartElementId,
        yAxisCaption,
        data,
        min,
        max,
        pointFormat,
        height,
        dataType
    ) {
        $('#' + chartElementId).highcharts({
            chart: {
                type: 'boxplot',
                height: height
            },
            title: {
                text:  title
            },
            xAxis: {
                categories: ['']
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
                        medianWidth: 3
                }
            },
            series: [{
                name: title,
                data: data,
                tooltip: {
                    headerFormat: '<b>{point.series.name}</b><br/>',
                    pointFormat:  pointFormat
                }
            }]
        });
    }

    function chartTypeMenu(chartElementId, chartTypes) {
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
                        chartTypes.map(function(name) {
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

    function changeCategoricalCountChartType(chartType, categories, datas, seriesSize, title, elementId, showLabels, showLegend, height, pointFormat) {
        var showLegendImpl = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map( function(data, index) {
                    var size = (100 / seriesSize) * (index + 1)
                    var innerSize = 0
                    if (index > 0)
                        innerSize = (100 / seriesSize) * index + 1
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                pieChart(title, elementId, series, showLabels, showLegend, height, true, true);
                break;
            case 'Column':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                columnChart(title, elementId, categories, series, false, '', 'Count', true, showLegendImpl, pointFormat, height, null, true, true);
                break;
            case 'Bar':
                var colorByPoint = (seriesSize == 1)
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: colorByPoint};
                });

                columnChart(title, elementId, categories, series, true, '', 'Count', true, showLegendImpl, pointFormat, height, null, true, true);
                break;
            case 'Line':
                var series = datas

                lineChart(title, elementId, categories, series, '', 'Count', showLegendImpl, true, pointFormat, height, null, true, true);
                break;
            case 'Polar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                polarChart(title, elementId, categories, series, showLegendImpl, height, null, true, true);
                break;
        }
    }

    function changeNumericalCountChartType(chartType, datas, seriesSize, title, fieldLabel, elementId, height, pointFormat, dataType) {
        var showLegend = seriesSize > 1

        switch (chartType) {
            case 'Pie':
                var series = datas.map( function(data, index) {
                    var size = (100 / seriesSize) * index
                    var innerSize = Math.max(0, (100 / seriesSize) * (index - 1) + 1)
                    return {name: data.name, data: data.data, size: size + '%', innerSize: innerSize + '%'};
                });

                pieChart(title, elementId, series, false, showLegend, height, false, true);
                break;
            case 'Column':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                columnChart(title, elementId, null, series, false, fieldLabel, 'Count', false, showLegend, pointFormat, height, dataType, false, true);
                break;
            case 'Bar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, colorByPoint: false};
                });

                columnChart(title, elementId, null, series, true, fieldLabel, 'Count', false, showLegend, pointFormat, height, dataType, false, true);
                break;
            case 'Line':
                var series = datas

                lineChart(title, elementId, null, series, fieldLabel, 'Count', showLegend, true, pointFormat, height, dataType, false, true);
                break;
            case 'Polar':
                var series = datas.map( function(data, index) {
                    return {name: data.name, data: data.data, type: 'area', pointPlacement: 'on'};
                });

                polarChart(title, elementId, null, series, showLegend, height, dataType, false, true);
                break;
        }
    }
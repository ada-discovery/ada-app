    function pieChart(
        title,
        chartElementName,
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
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementName).highcharts({
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
                        format: '<b>{point.name}</b>: {point.percentage:.1f}%',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    },
                    showInLegend: showLegend,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementName).trigger("pointClick", this);
                            }
                        }
                    }
                },
                series: {
                    animation: {
                        duration: 700
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
        chartElementName,
        categories,
        series,
        inverted,
        xAxisCaption,
        yAxisCaption,
        showLegend,
        height,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        var chartType = 'column'
        if (inverted)
            chartType = 'bar'
        $('#' + chartElementName).highcharts({
            chart: {
                type: chartType,
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
//                type: 'category',
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
                        enabled: true,
                        format: '{point.y:.1f}'
                    },
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementName).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 700
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: '<span style="color:{point.color}">{point.name} (Count)</span>: <b>{point.y:.2f}</b><br/>'
            },

            series: series
        })
    }

    function timeLineChart(
        title,
        chartElementName,
        xAxisCaption,
        yAxisCaption,
        series,
        height
    ) {
        $('#' + chartElementName).highcharts({
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
                borderWidth: 0
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
        chartElementName,
        categories,
        series,
        xAxisCaption,
        yAxisCaption,
        showLegend,
        height,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementName).highcharts({
            chart: {
                height: height
            },
            title: {
                text: title
            },
            xAxis: {
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
                enabled: showLegend
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            plotOptions: {
                series: {
                    marker: {
                        enabled: true,
                        states: {
                            hover: {
                                enabled: true
                            }
                        }
                    },
                    allowPointSelect: allowPointClick,
                    cursor: cursor,
                    point: {
                        events: {
                            click: function () {
                                if (allowPointClick)
                                    $('#' + chartElementName).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 700
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: '<span style="color:{point.color}">{point.name} (Count)</span>: <b>{point.y:.2f}</b><br/>'
            },

            series: series
        })
    }

    function polarChart(
        title,
        chartElementName,
        categories,
        series,
        showLegend,
        height,
        allowPointClick,
        allowChartTypeChange
    ) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementName).highcharts({
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
                //align: 'center',
                //verticalAlign: 'bottom',
                //layout: 'vertical',
                enabled: showLegend
            },
            xAxis: {
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
                                    $('#' + chartElementName).trigger("pointClick", this);
                            }
                        }
                    },
                    animation: {
                        duration: 700
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
        chartElementName,
        xAxisCaption,
        yAxisCaption,
        series,
        height
    ) {
        $('#' + chartElementName).highcharts({
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
                borderWidth: 0
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

    function chartTypeMenu(chartElementName, chartTypes) {
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
                                    $('#' + chartElementName).trigger("chartTypeChanged", name);
                                }
                            }
                        })
                }
            }
        };
    }
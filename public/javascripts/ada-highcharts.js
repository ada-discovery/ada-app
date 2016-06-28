    function pieChart(title, chartElementName, showLabels, showLegend, data, allowPointClick, allowChartTypeChange) {
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
                type: 'pie'
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
                }
            },
            legend: {
                maxHeight: 70
            },
            credits: {
                enabled: false
            },
            exporting: exporting,
            series: [{
                name: title,
                colorByPoint: true,
                data: data
            }]
        });
    }

    function columnChart(title, chartElementName, xAxisCaption, yAxisCaption, inverted, categories, data, allowPointClick, allowChartTypeChange) {
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
                type: chartType
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
                enabled: false
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
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: '<span style="color:{point.color}">{point.name} (Count)</span>: <b>{point.y:.2f}</b><br/>'
            },

            series: [{
                name: title,
                colorByPoint: true,
                data: data
            }]
        })
    }

    function lineChart(title, chartElementName, xAxisCaption, yAxisCaption, categories, data, allowPointClick, allowChartTypeChange) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementName).highcharts({
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
                enabled: false
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
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: '<span style="color:{point.color}">{point.name} (Count)</span>: <b>{point.y:.2f}</b><br/>'
            },

            series: [{
                name: title,
                data: data
            }]
        })
    }

    function polarChart(title, chartElementName, categories, data, allowPointClick, allowChartTypeChange) {
        var chartTypes = ["Pie", "Column", "Bar", "Line", "Polar"];
        var exporting = {};
        if (allowChartTypeChange)
            exporting = chartTypeMenu(chartElementName, chartTypes)

        var cursor = '';
        if (allowPointClick)
            cursor = 'pointer';

        $('#' + chartElementName).highcharts({
            chart: {
                polar: true
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
                enabled: false
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
            series: [{
                name: title,
                type: 'area',
                pointPlacement: 'on',
                data: data
            }]

        });
    }

    function scatterChart(title, chartElementName, xAxisCaption, yAxisCaption, height, series) {
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
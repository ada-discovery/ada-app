    function pieChart(title, chartElementName, showLabels, showLegend, data) {
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
                    allowPointSelect: true,
                    cursor: 'pointer',
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
            series: [{
                name: title,
                colorByPoint: true,
                data: data
            }]
        });
    }

    function columnChart(title, chartElementName, xAxisCaption, yAxisCaption, categories, data) {
        $('#' + chartElementName).highcharts({
            chart: {
                type: 'column'
            },
            title: {
                text: title
            },
            xAxis: {
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
            plotOptions: {
                series: {
                    borderWidth: 0,
                    dataLabels: {
                        enabled: true,
                        format: '{point.y:.1f}'
                    }
                }
            },

            tooltip: {
                headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                pointFormat: '<span style="color:{point.color}">{point.name}</span>: <b>{point.y:.2f}</b><br/>'
            },

            series: [{
                name: title,
                colorByPoint: true,
                colorByPoint: true,
                data: data
            }]
        })
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
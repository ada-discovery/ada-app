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
                maxHeight: 70,
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
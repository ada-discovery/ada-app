$(function () {

    /**
     * Create a global exportCharts method that takes an array of charts as an argument,
     * and exporting options as the second argument
     */
    Highcharts.exportCharts = function (charts, options) {
        options = Highcharts.merge(Highcharts.getOptions().exporting, options);

        function exportFun(chartId, svgCallback) {
            const chart = $("#" + chartId).highcharts()
            if (chart) {
                chart.getSVGForLocalExport(options, {}, function () {
                    console.log("Failed to get SVG");
                    svgCallback(null);
                }, function (svg) {
                    svgCallback(svg);
                });
            } else {
                svgCallback(null)
            }
        }

        // Get SVG asynchronously and then download the resulting SVG
        combineSVGs(charts, exportFun, function (svg) {
            Highcharts.downloadSVGLocal(
                svg,
                options,
                // (options.filename || 'chart')  + '.' + (imageType === 'image/svg+xml' ? 'svg' : imageType.split('/')[1]),
                // imageType,
                // options.scale || 2,
                function () {
                    console.log("Failed to export on client side");
                }
            );
        });
    };

    // Set global default options for all charts
    Highcharts.setOptions({
        exporting: {
            fallbackToExportServer: false // Ensure the export happens on the client side or not at all
        }
    });
});
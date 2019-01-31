$.widget( "custom.ada_charts_heatmap", {
    // default options
    options: {},

    // the constructor
    _create: function() {
        var that = this;

        this.container = document.querySelector('#' + this.element.attr("id"));
        this.heatmap = AdaCharts.chart({chartType: 'heatmap', container: this.container});
        this._addExportButton();
    },

    update: function(data) {
      this.data = data;
      this.refresh();
    },

    refresh: function() {
        if (this.data) {
            this.heatmap.update(this.data);
            this.exportButton.show();
        }
    },

    _addExportButton: function() {
        var that = this;

        var exportButton = "<a id='heatmapExportButton' href='#' class='btn btn-default btn-sm' style='display:none; position: absolute; right: 15px; top: -15px;' title='Export to PNG'><span class='glyphicon glyphicon glyphicon-export'></span></a>"
        this.element.prepend(exportButton)
        this.exportButton = this.element.find("#heatmapExportButton")

        this.exportButton.on('click', function () {
            that.exportToImage();
        });
    },

    exportToImage: function() {
        this.heatmap.toPNG();
    }
})
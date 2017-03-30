$.widget( "custom.dynamicTable", {
    // default options
    options: {
        domainName: null,
        rowToModel: null,
        itemToRow: null,
        sortable: false
    },

    // the constructor
    _create: function() {
        var that = this;
        var domainName = this.options.domainName;
        this.tableDiv = this.element;
        this.tableBody = this.tableDiv.find("table tbody")

        this.modalName = 'add_' + domainName + 'Modal'
        this.submitButtonName = domainName + 'SubmitButton'
        this.itemsName = domainName + 's[]'

        handleModalButtonEnterPressed(this.modalName, this.submitButtonName, function() {that.addTableRowFromModal()})

        $('form').submit(function(ev) {
            ev.preventDefault();
            that.updateModelFromTable();
            this.submit();
        });

        // note that sortable (draggable) functionality requires jquery-ui js lib
        if (this.options.sortable) {
            this.tableBody.sortable();
        }
    },

    openAddModal: function() {
        $('#' + this.modalName +' input, #' + this.modalName +' select').each(function () {
            if (this.id)
                $(this).val("");
        })
        $('#' + this.modalName).modal()
    },

    _getModalValues: function() {
        var values = {};
        $('#' + this.modalName +' input, #' + this.modalName +' select').each(function () {
            if (this.id) {
                values[this.id] = $(this).val()
            }
        })
        return values;
    },

    addTableRowFromModal: function() {
        this.addTableRow(this._getModalValues())
    },

    addTableRow: function(values) {
        this.tableBody.append(this.options.itemToRow(values))
    },

    removeRows: function() {
        var that = this;
        this.tableBody.find('tr').each(function() {
            var checked = $(this).find("td input.table-selection[type=checkbox]").is(':checked');
            if (checked) {
                $(this).remove()
            }
        });
    },

    removeTableModel: function() {
        this.tableDiv.find("input[name='" + this.itemsName + "']").each(function() {
            $(this).remove();
        });
    },

    updateModelFromTable: function() {
        this.removeTableModel();
        this.addToModelFromTable();
    },

    addToModelFromTable: function() {
        var that = this;
        this.tableBody.find('tr').each(function() {
            var item = that.options.rowToModel($(this));
            var map = {};
            map[that.itemsName] = item;
            addParams(that.tableDiv, map)
        });
    },

    getModel: function() {
        var model = this.tableDiv.find("input[name='" + this.itemsName + "']").map(function() {
            return $(this).val().trim();
        }).get();
        return model;
    }
})
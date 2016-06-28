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

        handleModalButtonEnterPressed(this.modalName, this.submitButtonName, function() {that.addItems()})

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
        $('#' + this.modalName + ' #newItems').val("")
        $('#' + this.modalName).modal()
    },

    addItems: function() {
        var that = this;
        $.each($('#' + this.modalName + ' #newItems').val().split(","), function(index, item) {
            that.addTableRow(item.trim());
        })
    },

    addTableRow: function(item) {
        this.tableBody.append(this.options.itemToRow(item))
    },

    removeItems: function() {
        var that = this;
        this.tableBody.find('tr').each(function() {
            var checked = $(this).find("td input[type=checkbox]").is(':checked');
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
    }
})
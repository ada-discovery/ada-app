$.widget( "custom.multiFilter", {
    // default options
    options: {
        jsonConditions: null,
        fieldNameAndLabels: null,
        getFieldsCall: null,
        submitCall: null,
        filterSubmitParamName: null,
        listFilters: null
    },

    // the constructor
    _create: function() {
        var that = this;
        this.jsonConditions = this.options.jsonConditions;
        this.conditionFields = ["fieldName", "conditionType", "value"];
        this.fieldNameAndLabels = this.options.fieldNameAndLabels;

        // initialize elements
        this.filterTypeaheadElement = this.element.find("#filterTypeahead");
        this.filterIdElement = this.element.find("#filterId");
        this.addEditConditionModalElement = this.element.find("#addEditConditionModal");
        this.fieldNameTypeaheadElement = this.element.find("#fieldNameTypeahead");
        this.fieldNameElement = this.element.find("#fieldName");
        this.valueElement = this.element.find("#value");
        this.saveFilterModalElement = this.element.find("#saveFilterModal");
        this.saveFilterNameElement = this.element.find("#saveFilterName");
        this.showChoicesButtonElement = this.element.find("#showChoicesButton");
        this.conditionPanelElement = this.element.find("#conditionPanel");
        this.filterNameButtonElement = this.element.find("#filterNameButton");
        this.loadFilterModalElement = this.element.find("#loadFilterModal");
        this.loadFilterButtonElement = this.element.find("#loadFilterButton");

        if (this.fieldNameAndLabels) {
            that.initShowFieldNameLabelChoices();
            that.repopulateFieldNameTypeahead();
        }

        if (this.options.listFilters) {
            that.loadFilterSelection();
        }

        this.addEditConditionModalElement.on('shown.bs.modal', function () {
            if ($(this).find("#conditionIndex:first").val())
                that.valueElement.focus();
            else
                that.fieldNameTypeaheadElement.focus();
        })

        $(".condition-full").hover(null, function() {
            var buttons = $(this).find(".condition-buttons").first();
            buttons.fadeOut( 400 );
        });

        $(".condition").click(function() {
            var buttons = $(this).parent().children(".condition-buttons").first();
            buttons.fadeIn( 400 );
        });

        this.saveFilterModalElement.on('shown.bs.modal', function () {
            that.saveFilterNameElement.focus();
        })

        this.saveFilterNameElement.keypress(function (e) {
            if (e.which == 13) {
                that.saveFilterModalElement.modal('hide');
                that.saveFilter();
                return false;
            }
        });

        this.showChoicesButtonElement.click(function() {
            that.repopulateFieldNameTypeahead();
        });
    },

    showAddConditionModal: function() {
        this.loadFieldNames();
        this.addEditConditionModalElement.find("#conditionIndex").first().val("");

        var condition = {conditionType : "="};
        this.updateModalFromModel(condition, this.conditionFields);

        this.addEditConditionModalElement.modal('show');
    },

    showEditConditionModal: function (index) {
        this.loadFieldNames();
        this.addEditConditionModalElement.find("#conditionIndex").first().val(index);

        var condition = this.jsonConditions[index]
        this.updateModalFromModel(condition, this.conditionFields);

        this.addEditConditionModalElement.modal('show');
    },

    addEditConditionFinished: function () {
        var index =  this.addEditConditionModalElement.find("#conditionIndex").val();
        if (index)
            this.editAndSubmitConditionFromModal(index)
        else
            this.addAndSubmitConditionFromModal()
    },

    addAndSubmitConditionFromModal: function () {
        var condition = { };

        this.updateModelFromModal(condition, this.conditionFields);

        this.addAndSubmitCondition(condition);
    },

    editAndSubmitConditionFromModal: function (index) {
        var condition = this.jsonConditions[index];

        this.updateModelFromModal(condition, this.conditionFields);
        this.updateFilterFromModel(index, condition, this.conditionFields);

        this.submitFilter();
    },

    addAndSubmitCondition: function (condition) {
        this.jsonConditions.push(condition);
        this.submitFilter();
    },

    deleteCondition: function (index) {
        this.jsonConditions.splice(index, 1);
        this.submitFilter();
    },

    getJsonConditions: function () {
        return this.jsonConditions;
    },

    submitFilter: function () {
        this.submitFilterCustom(this.jsonConditions, null);
    },

    submitFilterCustom: function (conditions, filterId) {
        var params = getQueryParams(this.options.submitCall)

        var filterIdOrConditions = {};

        if (filterId) {
            filterIdOrConditions[this.options.filterSubmitParamName] = filterId;
        } else if (conditions) {
            filterIdOrConditions[this.options.filterSubmitParamName] = JSON.stringify(conditions);
        }

        $.extend(params, filterIdOrConditions);

        submit('get', this.options.submitCall, params);
    },

    // update functions
    updateModelFromModal: function (condition, fields) {
        var that = this;
        $.each(fields, function( i, field ) {
            condition[field] = that.addEditConditionModalElement.find("#" + field).first().val();
        })
    },

    updateModalFromModel: function (condition, fields) {
        var that = this;
        $.each(fields,  function( i, field ) {
            that.addEditConditionModalElement.find("#" + field).first().val(condition[field]);
        })
        var fieldNameTypeahead = this.addEditConditionModalElement.find("#fieldNameTypeahead").first();

        var elementToSelect = condition["fieldName"];
        if (condition["fieldLabel"])
            elementToSelect = condition["fieldLabel"];
    //        var elementToSelect = {name: condition["fieldName"], label: condition["fieldLabel"], isLabel: true};

        fieldNameTypeahead.typeahead('val', elementToSelect);
    },

    updateFilterFromModel: function (index, condition, fields) {
        var that = this;
        $.each(fields, function( i, field ) {
            that.element.find("#condition-full" + index +" #" + field).first().html(condition[field]);
        })
    },

    loadFieldNames: function () {
        var that = this;
        if (!this.fieldNameAndLabels) {
                if(this.options.getFieldsCall) {
                    $.ajax({
                        url: this.options.getFieldsCall,
                        success: function (data) {
                            that.fieldNameAndLabels = data.map( function(field, index) {
                                return {name: field.name, label: field.label};
                            });
                            that.initShowFieldNameLabelChoices();
                            that.repopulateFieldNameTypeahead();
                        },
                        async: false
                    });
                }
            }
        },

    disableShowChoice: function (value) {
        var showChoiceElement = $("input[name='showChoice'][value='" + value + "']")
        showChoiceElement.attr("disabled", true);
        showChoiceElement.parent().attr("title", "Disabled because no labels available");
    },

    initShowFieldNameLabelChoices: function () {
        var itemsWithNonNullLabel = $.grep(this.fieldNameAndLabels, function(item) {
            return item.label != null
        });

        // if no labels available disable the options mentioning labels and set the choice to 0 (names only)
        if (itemsWithNonNullLabel.length == 0) {
            $("input[name='showChoice'][value='0']").prop('checked', true);
            this.disableShowChoice("1");
            this.disableShowChoice("2");
            this.disableShowChoice("3");
        }
    },

    repopulateFieldNameTypeahead: function () {
        var choiceElement = $("input[name='showChoice']:checked")[0]
        if (choiceElement) {
            var choiceValue = parseInt(choiceElement.value);

            this.fieldNameTypeaheadElement.typeahead('destroy');

            populateFieldTypeahed(
                this.fieldNameTypeaheadElement,
                this.fieldNameElement,
                this.fieldNameAndLabels,
                choiceValue
            )
        };
    },

    saveFilter: function () {
        var name = this.saveFilterNameElement.val();
        var fullFilter = {_id: null, name: name, timeCreated: 0, conditions: this.jsonConditions};
        filterJsRoutes.controllers.dataset.FilterDispatcher.saveAjax(JSON.stringify(fullFilter)).ajax( {
            success: function(data) {
                showMessage("Filter '" + name + "' successfully saved.");
            },
            error: function(data){
                showError(data.responseText);
            }
        });
    },

    loadFilter: function () {
        var filterId = this.filterIdElement.val();
        this.submitFilterCustom([], filterId);
    },

    showConditionPanel: function () {
        this.conditionPanelElement.show();
        this.filterNameButtonElement.hide();
    },

    loadFilterSelectionAndShowModal: function () {
        // always reload new (saved) filters before opening modal
        this.loadFilterSelection();
        this.loadFilterModalElement.modal('show');
    },

    loadFilterSelection: function () {
        var that = this;
        if(this.options.listFilters) {
            $.ajax({
                url: this.options.listFilters,
                success: function (data) {
                    var filterTypeaheadData = data.map( function(filter, index) {
                        return {name: filter._id.$oid, label: filter.name};
                    });
                    that.filterTypeaheadElement.typeahead('destroy');
                    if (filterTypeaheadData.length > 0) {
                        populateFieldTypeahed(
                            that.filterTypeaheadElement,
                            that.filterIdElement,
                            filterTypeaheadData,
                            1
                        );
                    } else {
                        that.loadFilterButtonElement.hide();
                    }
                },
                async: false
            });
        }
    }
})
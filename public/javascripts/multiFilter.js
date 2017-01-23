$.widget( "custom.multiFilter", {
    // default options
    options: {
        jsonConditions: null,
        fieldNameAndLabels: null,
        getFieldsUrl: null,
        submitUrl: null,
        listFiltersUrl: null,
        filterSubmitParamName: null,
        filterId: null,
        createSubmissionJson: null,
        initFilterIfNeededCallback: null,
        fieldDisplayChoiceCallback: null
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
        var saveFilterModalName = "saveFilterModal" + this.element.attr('id');
        this.saveFilterModalElement = this.element.find("#" + saveFilterModalName);
        this.saveFilterNameElement = this.element.find("#saveFilterName");
        this.showChoicesPanelElement = this.element.find("#showChoicesPanel");
        this.showChoicesButtonElement = this.element.find("#showChoicesButton");
        this.conditionPanelElement = this.element.find("#conditionPanel");
        this.filterNameButtonElement = this.element.find("#filterNameButton");
        this.loadFilterModalElement = this.element.find("#loadFilterModal");
        this.loadFilterButtonElement = this.element.find("#loadFilterButton");

        if (this.fieldNameAndLabels) {
            that.initShowFieldNameLabelChoices();
            that.repopulateFieldNameChoices();
        }

        if (this.options.listFiltersUrl) {
            that.loadFilterSelection();
        }

        this.addEditConditionModalElement.on('shown.bs.modal', function () {
            if ($(this).find("#conditionIndex:first").val())
                that.valueElement.focus();
//            else
//                that.fieldNameTypeaheadElement.focus();
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

        this.element.find('input[name="showChoice"]').change(function() {
            that.repopulateFieldNameChoices();
            that.showChoicesPanelElement.collapse('hide');
        });

        //this.showChoicesButtonElement.click(function() {
        //    that.repopulateFieldNameChoices();
        //});
    },

    showAddConditionModal: function() {
        this.initFilterIfNeeded();
        this.addEditConditionModalElement.find("#conditionIndex").first().val("");

        var condition = {conditionType : "="};
        this.updateModalFromModel(condition, this.conditionFields);

        this.addEditConditionModalElement.modal('show');
    },

    showEditConditionModal: function (index) {
        this.initFilterIfNeeded();
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

    getJsonFilterId: function () {
        var filterIdFromElement = this.filterIdElement.val();
        var outputFilterId = (filterIdFromElement) ? filterIdFromElement : this.options.filterId;
        return (outputFilterId) ? {'$oid': outputFilterId} : null;
    },

    submitFilter: function () {
        this.submitFilterOrId(this.jsonConditions, null);
    },

    submitFilterOrId: function (conditions, filterId) {
        var params = getQueryParams(this.options.submitUrl)

        var filterIdOrConditions =
            (this.options.createSubmissionJson) ?
                this.options.createSubmissionJson(conditions, filterId)
            :
                this._defaultCreateSubmissionJson(conditions, filterId);

        $.extend(params, filterIdOrConditions);

        submit('get', this.options.submitUrl, params);
    },

    _defaultCreateSubmissionJson: function (conditions, filterId) {
        var filterOrId = (filterId) ? {'$oid': filterId} : conditions;

        var filterIdOrConditions = {};
        filterIdOrConditions[this.options.filterSubmitParamName] = JSON.stringify(filterOrId);

        return filterIdOrConditions;
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

    initFilterIfNeeded: function () {
        var that = this;
        if (!this.fieldNameAndLabels) {
                if(this.options.getFieldsUrl) {
                    $.ajax({
                        url: this.options.getFieldsUrl,
                        success: function (data) {
                            that.fieldNameAndLabels = data.map( function(field, index) {
                                return {name: field.name, label: field.label};
                            });
                            if (that.options.initFilterIfNeededCallback) {
                                that.options.initFilterIfNeededCallback();
                            }
                            that.initShowFieldNameLabelChoices();
                            that.repopulateFieldNameChoices();
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

    repopulateFieldNameChoices: function () {
        var that = this;
        var choiceElement = this.element.find('input[name="showChoice"]:checked')[0]
        if (choiceElement) {
            var choiceValue = parseInt(choiceElement.value);

            this.fieldNameTypeaheadElement.typeahead('destroy');

            populateFieldTypeahed(
                this.fieldNameTypeaheadElement,
                this.fieldNameElement,
                this.fieldNameAndLabels,
                choiceValue
            )

            this.fieldNameTypeaheadElement.typeahead('val', '');

            if (this.options.fieldDisplayChoiceCallback) {
                this.options.fieldDisplayChoiceCallback(choiceValue)
            }
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
        this.submitFilterOrId([], filterId);
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
        if(this.options.listFiltersUrl) {
            $.ajax({
                url: this.options.listFiltersUrl,
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
    },

    setFieldTypeaheadAndName: function(name, typeaheadVal) {
        this.fieldNameTypeaheadElement.typeahead('val', typeaheadVal);
        this.fieldNameElement.val(name)
    }
})
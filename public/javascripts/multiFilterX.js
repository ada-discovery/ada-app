$.widget( "custom.multiFilter", {
    // default options
    options: {
        jsonConditions: null,
        fieldNameAndLabels: null,
        getFieldsUrl: null,
        submitUrl: null,
        refreshAjaxFun: null,
        listFiltersUrl: null,
        saveFilterAjaxFun: null,
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
        this.filterId = this.options.filterId;
        this.modelHistory = [];
        this.conditionFields = ["fieldName", "conditionType", "value"];
        this.fieldNameAndLabels = this.options.fieldNameAndLabels;

        // initialize elements
        this.filterTypeaheadElement = this.element.find("#filterTypeahead");
        this.addEditConditionModalElement = this.element.find("#addEditConditionModal");
        this.fieldNameTypeaheadElement = this.element.find("#fieldNameTypeahead");
        this.fieldNameElement = this.element.find("#fieldName");
        this.filterIdModalElement = this.element.find("#filterId");
        var saveFilterModalName = "saveFilterModal" + this.element.attr('id');
        this.saveFilterModalElement = this.element.find("#" + saveFilterModalName);
        this.saveFilterNameElement = this.element.find("#saveFilterName");
        this.showChoicesPanelElement = this.element.find("#showChoicesPanel");
        this.showChoicesButtonElement = this.element.find("#showChoicesButton");
        this.loadFilterModalElement = this.element.find("#loadFilterModal");
        this.loadFilterButtonElement = this.element.find("#loadFilterButton");
        this.saveFilterButtonElement = this.element.find("#saveFilterButton");
        this.rollbackFilterButtonElement = this.element.find("#rollbackFilterButton");

        if (this.fieldNameAndLabels) {
            that._initShowFieldNameLabelChoices();
            that._repopulateFieldNameChoices();
        }

        this.addEditConditionModalElement.on('shown.bs.modal', function () {
            if ($(this).find("#conditionIndex:first").val())
                $(this).find("#value").focus();
//            else
//                that.fieldNameTypeaheadElement.focus();
        })

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
            that._repopulateFieldNameChoices();
            that.showChoicesPanelElement.collapse('hide');
        });

        this.initConditionButtons();
        //this.showChoicesButtonElement.click(function() {
        //    that._repopulateFieldNameChoices();
        //});
    },

    _conditionPanelElement: function() {
        return this.element.find("#conditionPanel");
    },

    _filterNameButtonElement: function() {
        return this.element.find("#filterNameButton");
    },

    initConditionButtons: function() {
        $(".condition-full").hover(null, function() {
            var buttons = $(this).find(".condition-buttons").first();
            buttons.fadeOut( 400 );
        });

        $(".condition").click(function() {
            var buttons = $(this).parent().children(".condition-buttons").first();
            buttons.fadeIn( 400 );
        });
    },

    showAddConditionModal: function() {
        var that = this;
        this._initFilterIfNeeded(function() {
            that.addEditConditionModalElement.find("#conditionIndex").first().val("");

            var condition = {conditionType : "="};
            that._updateModalFromModel(condition, that.conditionFields);

            that.addEditConditionModalElement.modal('show');
        });
    },

    showEditConditionModal: function (index) {
        var that = this;
        this._initFilterIfNeeded(function() {
            that.addEditConditionModalElement.find("#conditionIndex").first().val(index);

            // set the value element to a text field (by default)
            var newValueElement = "<input id='value' class='float-left conditionValue' placeholder='Condition'/>"
            var valueElement = that.addEditConditionModalElement.find(".conditionValue")
            valueElement.replaceWith(newValueElement)

            var condition = that.jsonConditions[index]
            that._updateModalFromModel(condition, that.conditionFields);

            that.addEditConditionModalElement.modal('show');
        });
    },

    addEditConditionFinished: function () {
        var index =  this.addEditConditionModalElement.find("#conditionIndex").val();
        if (index)
            this._editAndSubmitConditionFromModal(index)
        else
            this._addAndSubmitConditionFromModal()
    },

    _addAndSubmitConditionFromModal: function () {
        this._addModelToHistoryAndClearFilterId();

        var condition = { };
        this._updateConditionFromModal(condition, this.conditionFields);
        this._updateConditionFromModalAux(condition, "fieldNameTypeahead", "fieldLabel");

        this.jsonConditions.push(condition);

        this._submitFilter();
    },

    _editAndSubmitConditionFromModal: function (index) {
        this._addModelToHistoryAndClearFilterId();

        var condition = this.jsonConditions[index];
        this._updateConditionFromModal(condition, this.conditionFields);
        this._updateConditionFromModalAux(condition, "fieldNameTypeahead", "fieldLabel");

        var fields = this.conditionFields.concat(["fieldLabel"])
        this._updateFilterFromModel(index, condition, fields);

        this._submitFilter();
    },

    addConditionsAndSubmit: function(conditions) {
        this._addModelToHistoryAndClearFilterId();

        var that = this;
        $.each(conditions, function(i, condition) {
            that.jsonConditions.push(condition);
        });

        this._submitFilter();
    },

    deleteCondition: function (index) {
        this._addModelToHistoryAndClearFilterId();

        this.jsonConditions.splice(index, 1);
        this._submitFilter();
    },

    getModel: function () {
        return this.jsonConditions;
    },

    getIdOrModel: function() {
        return (this.filterId) ? {'$oid': this.filterId} : this.jsonConditions;
    },

    setModel: function(jsonConditions) {
        this.jsonConditions = jsonConditions;
    },

    rollbackModelAndSubmit: function () {
        if (this.modelHistory.length > 0) {
            this.jsonConditions = this.modelHistory.pop();
            this.filterId = null;

            if (this.modelHistory.length == 0)
                this.rollbackFilterButtonElement.hide();

            this._submitFilter();
        }
    },

    _addModelToHistory: function() {
        var copiedModel = this.jsonConditions.map(function(condition) {
            return  Object.assign({}, condition);
        })
        this.modelHistory.push(copiedModel);

        if (this.options.refreshAjaxFun)
            this.rollbackFilterButtonElement.show();
    },

    _addModelToHistoryAndClearFilterId: function() {
        this._addModelToHistory();
        this.filterId = null;
        this.saveFilterButtonElement.show();
    },

    _submitFilter: function () {
        this._submitFilterOrId(this.jsonConditions, null);
    },

    _submitFilterOrId: function (conditions, filterId) {
        var params = getQueryParams(this.options.submitUrl)

        var filterIdOrConditions =
            (this.options.createSubmissionJson) ?
                this.options.createSubmissionJson(conditions, filterId)
            :
                this._defaultCreateSubmissionJson(conditions, filterId);

        $.extend(params, filterIdOrConditions);

        if (this.options.refreshAjaxFun) {
            this.options.refreshAjaxFun(this.options.submitUrl, params, this.element)
        } else {
            submit('get', this.options.submitUrl, params);
        }
    },

    _defaultCreateSubmissionJson: function (conditions, filterId) {
        var adjustedConditions = conditions.map(function(condition, index) {
            if (Array.isArray(condition.value))
                condition["value"] = condition.value.join(",")
            return condition;
        })

        var filterOrId = (filterId) ? {'$oid': filterId} : adjustedConditions;

        var filterIdOrConditions = {};
        filterIdOrConditions[this.options.filterSubmitParamName] = JSON.stringify(filterOrId);

        return filterIdOrConditions;
    },

    // update functions
    _updateConditionFromModal: function (condition, fields) {
        var that = this;
        $.each(fields, function( i, field ) {
            that._updateConditionFromModalAux(condition, field, field);
        })
    },

    _updateConditionFromModalAux: function (condition, fieldFrom, fieldTo) {
        var that = this;
        condition[fieldTo] = that.addEditConditionModalElement.find("#" + fieldFrom).first().val();
    },

    _updateModalFromModel: function (condition, fields) {
        var that = this;

        var fieldNameTypeahead = this.addEditConditionModalElement.find("#fieldNameTypeahead").first();

        var elementToSelect = condition["fieldName"];
        if (condition["fieldLabel"])
            elementToSelect = condition["fieldLabel"];
    //        var elementToSelect = {name: condition["fieldName"], label: condition["fieldLabel"], isLabel: true};

        fieldNameTypeahead.typeahead('val', elementToSelect)

        $.each(fields,  function( i, field ) {
            that.addEditConditionModalElement.find("#" + field).first().val(condition[field]);
        })
        selectShortestSuggestion(fieldNameTypeahead)
    },

    _updateFilterFromModel: function (index, condition, fields) {
        var that = this;
        $.each(fields, function( i, field ) {
            that._conditionPanelElement().find("#condition-full" + index +" #" + field).first().html(condition[field]);
        })
    },

    _createNewFilterCondition: function (index, condition) {
        var li = $("<li>", {id: "condition-full" + index, "class": "condition-full"});

        var conditionSpan = $("<span>", {"class": "condition"})
        conditionSpan.append("<span class='label label-primary' id='fieldLabel'>" + condition['fieldLabel'] + "</span>");
        conditionSpan.append("<span id='conditionType'><b> " + condition['conditionType'] + " </b></span>");
        conditionSpan.append("<span class='label label-primary' id='value'>" + condition['value'] + "</span>");

        var h4 = $("<h4></h4>");
        h4.append(conditionSpan)

        li.append(h4);
        this._conditionPanelElement().append(li)
    },

    saveFilter: function () {
        var name = this.saveFilterNameElement.val();
        var fullFilter = {_id: null, name: name, isPrivate: false, timeCreated: 0, conditions: this.jsonConditions};

        if (this.options.saveFilterAjaxFun)
            this.options.saveFilterAjaxFun(fullFilter)
    },

    loadFilterFromModal: function () {
        this._addModelToHistory();

        this.filterId = this.filterIdModalElement.val();

        this.saveFilterButtonElement.hide();
        this._submitFilterOrId([], this.filterId);
    },

    showConditionPanel: function () {
        this._conditionPanelElement().find(".filter-part").show();
        this._filterNameButtonElement().hide();
    },

    loadFilterSelectionAndShowModal: function () {
        // always reload new (saved) filters before opening modal
        var that = this;
        this._loadFilterSelection(function() {
            that.loadFilterModalElement.modal('show');
        });
    },

    setFieldTypeaheadAndName: function(name, typeaheadVal) {
        this.fieldNameTypeaheadElement.typeahead('val', typeaheadVal);
        this.fieldNameElement.val(name)
        selectShortestSuggestion(this.fieldNameTypeaheadElement)
    },

    _loadFilterSelection: function (successFun) {
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
                            that.filterIdModalElement,
                            filterTypeaheadData,
                            1
                        );
                        if (successFun)
                            successFun()
                    } else {
                        that.loadFilterButtonElement.hide();
                    }
                },
                error: function(data) {
                    showErrorResponse(data)
                }
            });
        }
    },


    _initFilterIfNeeded: function (successFun) {
        var that = this;
        if (!this.fieldNameAndLabels) {
            if(this.options.getFieldsUrl) {
                $.ajax({
                    url: this.options.getFieldsUrl,
                    success: function (data) {
                        that.fieldNameAndLabels = data.map( function(field, index) {
                            if (Array.isArray(field))
                                return {name: field[0], label: field[1]};
                            else
                                return {name: field.name, label: field.label};
                        });
                        if (that.options.initFilterIfNeededCallback) {
                            that.options.initFilterIfNeededCallback();
                        }
                        that._initShowFieldNameLabelChoices();
                        that._repopulateFieldNameChoices();
                        if (successFun)
                            successFun()
                    },
                    error: function(data) {
                        showErrorResponse(data)
                    }
                });
            }
        } else {
            if (successFun)
                successFun()
        }
    },

    _disableShowChoice: function (value) {
        var showChoiceElement = $("input[name='showChoice'][value='" + value + "']")
        showChoiceElement.attr("disabled", true);
        showChoiceElement.parent().attr("title", "Disabled because no labels available");
    },

    _initShowFieldNameLabelChoices: function () {
        var itemsWithNonNullLabel = $.grep(this.fieldNameAndLabels, function(item) {
            return item.label != null
        });

        // if no labels available disable the options mentioning labels and set the choice to 0 (names only)
        if (itemsWithNonNullLabel.length == 0) {
            $("input[name='showChoice'][value='0']").prop('checked', true);
            this._disableShowChoice("1");
            this._disableShowChoice("2");
            this._disableShowChoice("3");
        }
    },

    _repopulateFieldNameChoices: function () {
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
    }
})
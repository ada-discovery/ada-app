////////////
// Filter //
////////////

function activateDataSetFilter(filterElement, jsonConditions, filterId, submitAjaxFun, getFieldsUrl, listFiltersUrl) {
    var saveFilterAjaxFun = function(filter) {
        var filterJson = JSON.stringify(filter)
        filterJsRoutes.controllers.dataset.FilterDispatcher.saveAjax(filterJson).ajax( {
            success: function(data) {
                showMessage("Filter '" + filter.name + "' successfully saved.");
            },
            error: function(data) {
                showErrorResponse(data)
            }
        });
    }

    $(filterElement).multiCategoryFilter({
        jsonConditions: jsonConditions,
        getFieldsUrl: getFieldsUrl,
        submitAjaxFun: submitAjaxFun,
        listFiltersUrl: listFiltersUrl,
        saveFilterAjaxFun: saveFilterAjaxFun,
        filterSubmitParamName: "filterOrId",
        filterId: filterId,
        categoryTreeElementId: 'categoryTree'
    })

    addAllowedValuesUpdateForFilter(filterElement)
}

function saveFilterToView(viewId) {
    var filterElements = $("#filtersTr").find(".filter-div").toArray();

    var filterOrIds = filterElements.map(function(filterElement, index) {
        return $(filterElement).multiFilter('getIdOrModel');
    });

    dataViewJsRoutes.controllers.dataset.DataViewDispatcher.saveFilter(
        viewId,
        JSON.stringify(filterOrIds)
    ).ajax( {
        success: function() {
            showMessage("Filter successfully added to the view.");
        },
        error: function(data){
            showError( data.responseText );
        }
    });
}

function refreshViewOnFilterUpdate(viewId, filterOrId, filterElement, widgetGridElementWidth, enforceWidth) {
    var index = $("#filtersTr").find(".filter-div").index(filterElement);

    var counts = $("#filtersTr").find(".count-hidden").map(function(index, element) {
        return parseInt($(element).val());
    }).toArray();

    // add the old count to the params
    var totalCount = counts.reduce(function (a, b) {return a + b;}, 0);
    var oldCountDiff = totalCount - counts[index];

    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getViewElementsAndWidgetsCallback(viewId, "", filterOrId, oldCountDiff).ajax( {
        success: function(data) {
            // filter
            filterElement.multiFilter("replaceModelAndPanel", data.filterModel, data.conditionPanel);

            // display count
            var countDisplayElement = filterElement.closest(".row").parent().find(".count-div")
            countDisplayElement.html("<h3>" + data.count + "</h3>");

            // (hidden) count
            var countHiddenElement = filterElement.parent().find(".count-hidden")
            countHiddenElement.val(data.count);

            // page header
            $(".page-header").html("<h3>" + data.pageHeader + "</h3>");

            // table
            var tableElement = $("#tablesTr").find(".table-div:eq(" + index + ")")
            tableElement.html(data.table);

            // widgets
            var widgetsDiv = $("#widgetsTr > td:eq(" + index + ")")
            updateWidgetsFromCallback(data.widgetsCallbackId, widgetsDiv, filterElement, widgetGridElementWidth, enforceWidth)
        },
        error: function(data) {
            showErrorResponse(data)
        }
    });
}

function addNewViewColumn(viewId, widgetGridElementWidth, enforceWidth, activateFilter) {
    // total count
    var totalCount = getViewTotalCount();

    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getNewFilterViewElementsAndWidgetsCallback(viewId, "", totalCount).ajax( {
        success: function(data) {
            // filter
            var filterTd = $("<td style='padding-left: 10px; vertical-align:top'>")
            filterTd.html(data.countFilter)
            $("#filtersTr").append(filterTd)
            var filterElement = filterTd.find(".filter-div")
            activateFilter(filterElement, []);

            // page header
            $(".page-header").html("<h3>" + data.pageHeader + "</h3>");

            // widgets
            var widgetTd = $("<td style='vertical-align:top'>")
            $("#widgetsTr").append(widgetTd)
            refreshHighcharts();

            // table
            var tableTd = $("<td style='padding-left: 10px; padding-right: 10px; vertical-align:top'>")
            var tableDiv = $("<div class='table-div'>")
            tableTd.append(tableDiv)
            $("#tablesTr").append(tableTd)
            $(tableDiv).html(data.table);

            // get widgets from callback
            updateWidgetsFromCallback(data.widgetsCallbackId, widgetTd, filterElement, widgetGridElementWidth, enforceWidth)

            showMessage("New column/filter successfully added to the view.")
        },
        error: function(data) {
            showErrorResponse(data)
        }
    });
}

function getViewTotalCount() {
    var counts = $("#filtersTr").find(".count-hidden").map(function(index, element) {
        return parseInt($(element).val());
    }).toArray();

    // total count
    return counts.reduce(function (a, b) {return a + b;}, 0);
}

function addAllowedValuesUpdateForFilter(filterElement) {
    $(filterElement).find("#fieldNameTypeahead").on('typeahead:select', function (e, field) {
        dataSetJsRoutes2.controllers.dataset.DataSetDispatcher.getFieldTypeWithAllowedValues(field.key).ajax({
            success: function (data) {
                updateFilterValueElement($(filterElement), data)
            },
            error: function (data) {
                showError(data.responseText);
            }
        });
    });
}

/////////////
// Widgets //
/////////////

function updateWidgetsFromCallback(callbackId, widgetsDiv, filterElement, defaultElementWidth, enforceWidth, successMessage) {
    widgetsDiv.html("")
    addSpinner(widgetsDiv, "margin-bottom: 20px;")

    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getWidgets().ajax( {
        data: {
            "callbackId": callbackId
        },
        success: function(data) {
            if (successMessage) showMessage(successMessage)

            var widgets = data[0]

//                    var widgetHolders = widgetsDiv.find(".chart-holder")

            var row = $("<div class='row'>")
            $.each(widgets, function (j, widget) {
                row.append(widgetDiv(widget, defaultElementWidth, enforceWidth))
            })
            widgetsDiv.html(row)
            $.each(widgets, function (j, widget) {
                genericWidget(widget, filterElement)
            })
//                    $.each(widgetHolders, function(i, widgetHolder){
//                        genericWidgetForElement(widgetHolder.id, widgets[i], filterElement)
//                    })
        },
        error: function(data) {
            widgetsDiv.html("")
            hideMessages();
            showErrorResponse(data)
        }
    });
}

function updateAllWidgetsFromCallback(callbackId, defaultElementWidth) {
    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getWidgets().ajax( {
        data: {
            "callbackId": callbackId
        },
        success: function(data) {
            $("#widgetsTr").html("")
            $.each(data, function (i, widgets) {
                var td = $("<td style='vertical-align:top'>")
                var row = $("<div class='row'>")
                $.each(widgets, function (j, widget) {
                    row.append(widgetDiv(widget, defaultElementWidth))
                })
                td.append(row)
                $("#widgetsTr").append(td)
            })
            var filterElements = $("#filtersTr").find(".filter-div").toArray();
            $.each(data, function (i, widgets) {
                var filterElement = filterElements[i];
                $.each(widgets, function (j, widget) {
                    genericWidget(widget, filterElement)
                })
            })
        },
        error: function(data){
            $("#widgetsTr").html("")
            showErrorResponse(data)
        }
    });
}

///////////
// Table //
///////////

function showJsonFieldValue(id, fieldName, fieldLabel, isArray) {
    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getFieldValue(id, fieldName).ajax( {
        success: function(data) {
            var title =  fieldLabel
            var size = 0
            var content = "<p>No data to show.</p>"

            if (data) {
                content = JSON.stringify(data, null, "\t").replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;').replace(/\n/g, '</br>')
                size = data.length
            }

            if (isArray)
                title = title + ": Array (Size " + size + ")"

            $('#jsonModal #jsonModalBody').html(content)
            $('#jsonModal .modal-title').html(title)
            $('#jsonModal').modal();
        },
        error: function(data){
            showError(data.responseText);
        }
    });
}

function showArrayFieldChart(id, fieldName, fieldLabel) {
    dataSetJsRoutes.controllers.dataset.DataSetDispatcher.getFieldValue(id, fieldName).ajax( {
        success: function(data) {
            $("#lineChartDiv").html("")

            var series = []
            var pointFormat = '<span style="color:{point.color}">{point.x:.2f}</span>: <b>{point.y:.2f}</b><br/>'

            if (data && data.length > 0) {
                var flattenData = $.map(data, function (item, i) {
                    return flatten(item)
                });

                var firstItem = flattenData[0]

                var numericKeys = Object.keys(firstItem).filter(function(key) {
                    return !isNaN(firstItem[key])
                });

                series = $.map(numericKeys, function (key, j) {
                    var seriesData = $.map(flattenData, function (item, i) {
                        return item[key];
                    });
                    return [{name: key, data: seriesData}]
                });
            }

            $('#lineChartArrayModal').modal("show");

            $('#lineChartArrayModal').on('shown.bs.modal', function (e) {
                lineChart(fieldLabel, "lineChartDiv", null, series, 'Point', 'Value', true, false, pointFormat, null, null, false, false,  false);
            })
        },
        error: function(data){
            showError(data.responseText);
        }
    });
}
// submit synchronously to a provided URL (action) and parameters.
function submit(method, action, parameters) {
  var form = $('<form></form>');

  form.attr("method", method);
  form.attr("action", action);

  if (parameters)
    $.each(parameters, function(key, value) {
      var field = $('<input></input>');

      field.attr("type", "hidden");
      field.attr("name", key);
      field.attr("value", value);

      form.append(field);
    });

  $(document.body).append(form);
  form.submit();
}

function addParams(form, parameters) {
  $.each(parameters, function(key, value) {
    var field = $('<input></input>');

    field.attr("type", "hidden");
    field.attr("name", key);
    field.attr("value", value);

    form.append(field);
  });
}

function getQueryParams(qs) {
  qs = qs.split('+').join(' ');
  qs = qs.split("?").slice(1).join("?");

  var params = {},
      tokens,
      re = /[?&]?([^=]+)=([^&]*)/g;


  while (tokens = re.exec(qs)) {
    var paramName = decodeURIComponent(tokens[1]).replace("amp;", "");
    params[paramName] = decodeURIComponent(tokens[2]);
  }

  return params;
}

function stringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter, string) {
    var string2 = string;
    if (nonWhitespaceDelimiter) {
        string2 = string.split(nonWhitespaceDelimiter).join(" ")
    }
    var strings = Bloodhound.tokenizers.whitespace(string2);
    if (searchAsContainsFlag) {
        $.each(strings, function (k, v) {
            var i = 1;
            while ((i + 1) < v.length) {
                strings.push(v.substr(i, v.length));
                i++;
            }
        })
    }
    return strings;
}

function createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter) {
    return stringDatumTokenizer.bind(null, searchAsContainsFlag).bind(null, nonWhitespaceDelimiter)
}

function populateStringTypeahead(element, data, searchAsContainsFlag, nonWhitespaceDelimiter, updateValueElement) {
    var datumTokenizer = createStringDatumTokenizer(searchAsContainsFlag, nonWhitespaceDelimiter)
    populateTypeahead(element, data, datumTokenizer, null, null, updateValueElement)
}

function populateTypeahead(element, data, datumTokenizer, displayFun, suggestionFun, updateValueElement) {
  var dataSource = new Bloodhound({
    datumTokenizer: datumTokenizer,
    queryTokenizer: Bloodhound.tokenizers.whitespace,
    local: data
  });

  dataSource.initialize();

  // Setting of minlength to 0 does not work. To sho ALL items if nothing entered this function needs to be introduced
  function listSearchWithAll(q, sync) {
    if (q == '')
        sync(dataSource.all());
    else
        dataSource.search(q, sync);
  }

  element.typeahead({
    hint: true,
    highlight: true,
    minLength: 0
  }, {
    source: listSearchWithAll,
    display: displayFun,
    templates: {
      suggestion: suggestionFun
    },
    limit: 25
  });

  element.on("focus", function () {
    var value = element.val();
    element.typeahead('val', '_');
    element.typeahead('val', value);
    return true
  });

  element.on('typeahead:select', function (e, datum) {
    if (updateValueElement)
        updateValueElement(datum);
  });

  element.on('typeahead:cursorchanged', function (e, datum) {
    if (updateValueElement)
        updateValueElement(datum);
  });
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param fieldNameAndLabels
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeahed(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption) {
    var fullFieldNameAndLabels = fieldNameAndLabels.map( function(field, index) {
        var nameItem = {key: field.name, value: field.name};
        var labelItem = {key: field.name, value: field.label, isLabel: true}

        switch (showOption) {
            case 0: return [nameItem];
            case 1: return (field.label) ? [labelItem] : [];
            case 2: return (field.label) ? [labelItem] : [nameItem];
            case 3: return (field.label) ? [nameItem, labelItem] : [nameItem];
        }
    });

    var stringDatumTokenizer = createStringDatumTokenizer(true, false);

    var compareValues = function (a, b) {
        if (a.value < b.value)
            return -1;
        else if (a.value == b.value)
            return 0;
        else
            return 1;
    };

    populateTypeahead(
        typeaheadElement,
        [].concat.apply([], fullFieldNameAndLabels).sort(compareValues),
        function (item) {
            return stringDatumTokenizer(item.value);
        },
        function (item) {
            return item.value;
        },
        function (item) {
            var nameBadge = '';
            var labelBadge = '';
            switch (showOption) {
                case 0: nameBadge = ''; break;
                case 1: nameBadge = ''; break;
                case 2: nameBadge = '<span class="label label-info label-filter">name</span>'; break;
                case 3: nameBadge = '<span class="label label-info label-filter">name</span>'; break;
            }
            switch (showOption) {
                case 0: labelBadge = ''; break;
                case 1: labelBadge = ''; break;
                case 2: labelBadge = '<span class="label label-success label-filter">label</span>'; break;
                case 3: labelBadge = '<span class="label label-success label-filter">label</span>'; break;
            }
            if (item.isLabel)
                return '<div><span>' + item.value + '</span>' + labelBadge + '</div>';
            else
                return '<div><span>' + item.value + '</span>' + nameBadge + '</div>';
        },
        function (item) {
            fieldNameElement.val(item.key);
        }
    );
}

/**
 *
 * @param typeaheadElement
 * @param fieldNameElement
 * @param url
 * @param showOption 0 - show field names only, 1 - show field labels only,
 *                   2 - show field labels, and field names if no label defined, 3 - show both, field names and labels
 */
function populateFieldTypeahedFromUrl(typeaheadElement, fieldNameElement, url, showOption) {
    $.ajax({
        url: url,
        success: function (fieldNameAndLabels) {
            populateFieldTypeahed(typeaheadElement, fieldNameElement, fieldNameAndLabels, showOption);
        }
    });
}

function registerMessageEventSource(url) {
//  if (!!window.EventSource) {
 if (!window.messageSource)
    window.messageSource = new self.EventSource(url);
    window.messageSource.onmessage = function (e) {
      var json = $.parseJSON(e.data);
      prependTrollboxJsonMessage(json, true);
      $("#trollmessagebox").scrollTop($(document).height());
    };

    window.messageSource.addEventListener('error', function (e) {
      if (e.eventPhase == EventSource.CLOSED) {
        console.log("Connection was closed on error: " + e);
      } else {
        console.log("Error occurred while streaming: " + e);
      }
    }, false);
    //setTimeout(function() {
    //    console.log("Closing source");
    //    source.close()
    //}, 3000)
  //} else {
  //  console.log("No support for HTML-5 Event Source")
  //  prependTrollboxMessage("", "", "Sorry. This browser doesn't seem to support HTML5-based messaging. Check <a href='http://html5test.com/compare/feature/communication-eventSource.html'>html5test</a> for browser compatibility.");
  //}
}

function prependTrollboxJsonMessage(jsonMessage, isAdmin, fadeIn) {
    var createdBy = jsonMessage.createdByUser
    var isUserAdmin = jsonMessage.isUserAdmin
    var timeCreated = jsonMessage.timeCreated
    var content = Autolinker.link(jsonMessage.content);
    var date = new Date(timeCreated);
    prependTrollboxMessage(createdBy, date.toISOString(), content, isUserAdmin, fadeIn);
}

function prependTrollboxMessage(author, timeCreated, text, isAdmin, fadeIn) {
  var messageBlock = null
  if(author) {
    if(isAdmin) {
        messageBlock = $('<div class="alert alert-dismissable alert-success" data-toggle="tooltip" title="Published at: ' + timeCreated + '">')
    } else {
        messageBlock = $('<div class="alert alert-dismissable" data-toggle="tooltip" title="Published at: ' + timeCreated + '">')
    }
    messageBlock.append('<span class="glyphicon glyphicon-user"></span>&nbsp;')
    messageBlock.append('<strong>' + author + ':</strong> &nbsp;')
  } else {
    messageBlock = $('<div class="alert alert-dismissable alert-info" data-toggle="tooltip" title="Published at: ' + timeCreated + '">')
    messageBlock.append('<span class="glyphicon glyphicon-king"></span>&nbsp;')
    messageBlock.append('<strong>Ada:</strong> &nbsp;')
  }
  messageBlock.append(text)
  if(fadeIn) {
      messageBlock.hide();
  }
  $('#trollmessagebox').append(messageBlock);
  if(fadeIn) {
      messageBlock.fadeIn('2000');
  }
  messageBlock.tooltip({
    placement : 'top'
  });
}

function showMessage(text) {
  $('#messageDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var messageBlock = $('<div class="alert alert-dismissable alert-success">')
  messageBlock.append(closeX)
  messageBlock.append('<strong>Well done!</strong> ')
  messageBlock.append(text)
  $('#messageDiv').html(messageBlock);
  $('#messageDiv').fadeIn('2000');

  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function showError(message) {
  $('#errorDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
  innerDiv.append(closeX);
  innerDiv.append(message);
  $('#errorDiv').html(innerDiv);
  $('#errorDiv').fadeIn('2000');
  addMessageDividerIfNeeded();
  registerMessageDividerRemoval();
}

function hideMessages() {
  $('#messageDiv').fadeOut('2000');
  $('#messageDiv').html('');
}

function showErrors(errors) {
  if (errors.length > 0) {
    var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
    $('#errorDiv').hide();
    $('#errorDiv').html("");
    $.each(errors, function(index, error) {
      var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
      innerDiv.append(closeX);
      innerDiv.append(error.message);
      $('#errorDiv').append(innerDiv);
    });
    $('#errorDiv').fadeIn('2000');
    addMessageDividerIfNeeded();
    registerMessageDividerRemoval();
  }
}

function addMessageDividerIfNeeded() {
    var messagesCount = $('#messageContainer').find('.alert-dismissable').length
    if ($('#messageContainer .messageDivider').length == 0 && messagesCount > 0) {
        $('#messageContainer').append('<hr class="messageDivider"/>')
    }
}

function registerMessageDividerRemoval() {
    $('#messageContainer .alert-dismissable .close').click(function () {
        var messagesCount = $('#messageContainer').find('.alert-dismissable').length
        if (messagesCount == 1) {
            $('#messageContainer .messageDivider').remove();
        }
    });
}

function hideErrors() {
  $('#errorDiv').fadeOut('2000');
  $('#errorDiv').html('');
}

function getCheckedTableIds(tableId, objectIdName) {
  var ids = []
  $('#' + tableId + ' tbody tr').each(function() {
    var id = getRowId($(this), objectIdName)
    var checked = $(this).find("td input[type=checkbox]").is(':checked');
    if (checked) {
      ids.push(id)
    }
  });
  return ids
};

function getNearestRowId(element, objectIdName) {
  var row = element.closest('tr');
  return getRowId(row, objectIdName)
}

function getRowId(rowElement, objectIdName) {
  var idElement = rowElement.find('#' + objectIdName)
  var id = idElement.text().trim();
  if (!id) {
    id = idElement.val().trim();
  }
  return id
}

function handleModalButtonEnterPressed(modalName, submitButtonName, action) {
  $("#" + modalName).keypress(function (e) {
    if (e.keyCode == 13) {
      e.preventDefault();
      action()
      $("#" + modalName).modal("hide")
    }
  });

  $("#" + submitButtonName).click(action);
}
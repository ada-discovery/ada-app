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

// TODO: Extend to key-value pairs
function populateTypeahead(element, values) {
  var listSearch = new Bloodhound({
    datumTokenizer: Bloodhound.tokenizers.whitespace,
    queryTokenizer: Bloodhound.tokenizers.whitespace,
    local: values
  });

  // Setting of minlength to 0 does not work. To sho ALL items if nothing entered this function needs to be introduced
  function listSearchWithAll(q, sync) {
    if (q == '')
        sync(listSearch.all());
     else
        listSearch.search(q, sync);
  }

  element.typeahead({
        hint: true,
        highlight: true,
        minLength: 0
    },{
        source: listSearchWithAll,
        limit: 25
    }
  );

  element.on("focus", function () {
    var value = element.val();
    element.typeahead('val', '_');
    element.typeahead('val', value);
    return true
  });
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
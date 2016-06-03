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

function showMessage(text) {
  $('#messageDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var messageBlock = $('<div class="alert alert-dismissable alert-success">')
  messageBlock.append(closeX)
  messageBlock.append('<strong>Well done!</strong> ')
  messageBlock.append(text)
  $('#messageDiv').html(messageBlock);
  $('#messageDiv').fadeIn('2000');
}

function showError(message) {
  $('#errorDiv').hide();

  var closeX = '<button type="button" class="close" data-dismiss="alert">×</button>'
  var innerDiv = $('<div class="alert alert-dismissable alert-danger">');
  innerDiv.append(closeX);
  innerDiv.append(message);
  $('#errorDiv').html(innerDiv);
  $('#errorDiv').fadeIn('2000');
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
  }
}

function hideErrors() {
  $('#errorDiv').fadeOut('2000');
  $('#errorDiv').html('');
}

function getCheckedTableIds(tableId, objectIdName) {
  var ids = []
  $('#' + tableId + ' tbody tr').each(function() {
    var id = $(this).find('#' + objectIdName).text().trim();
    var checked = $(this).find("td input[type=checkbox]").is(':checked');
    if (checked) {
      ids.push(id)
    }
  });
  return ids
};

// remove this
function doTableSelectionAction(tableName, actionName) {
  var ids = getCheckedIds(tableName)
  if (ids.length == 0) {
    showError("No rows selected!")
  } else {
    var controller = $('#' + tableName).find('#domainName').val();
    post('/' + controller + '/' + actionName, {ids: ids})
  }
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
// submit synchronously to a provided URL (action) and parameters.
function submit(method, action, parameters) {
  var form = $('<form></form>');

  form.attr("method", method);
  form.attr("action", action);

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
    params[decodeURIComponent(tokens[1])] = decodeURIComponent(tokens[2]);
  }

  return params;
}
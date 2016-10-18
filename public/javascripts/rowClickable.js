jQuery(document).ready(function($) {
    $(".clickable-row").dblclick(function() {
        window.document.location = $(this).data("href");
    });
    $(".no-rowClicked").dblclick(function( event ) {
        event.stopPropagation();
    });
});
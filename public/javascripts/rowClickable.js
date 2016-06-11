jQuery(document).ready(function($) {
    $(".clickable-row").click(function() {
        window.document.location = $(this).data("href");
    });
    $(".no-rowClicked").click(function( event ) {
        event.stopPropagation();
    });
});
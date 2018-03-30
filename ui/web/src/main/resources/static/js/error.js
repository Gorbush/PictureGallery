/**
 * Created by sergii_puliaiev on 7/5/17.
 */
var ErrorMessage = {
    span: null,
    currentMessageId: null,

    hideMessage: function () {
        ErrorMessage.span.hide();
    },
    cleanMode: function () {
        this.span.removeClass("label-default");
        this.span.removeClass("label-primary");
        this.span.removeClass("label-success");
        this.span.removeClass("label-info");
        this.span.removeClass("label-warning");
        this.span.removeClass("label-danger");
    },
    showError: function (elementId, message) {
        this.cleanMode();
        this.span.addClass("label-danger");
        this.span.text(message);
        ErrorMessage.span.show();
        this.scheduleHide();
        if (validValue(elementId)) {
            this.blinkElement(elementId);
        }
    },
    showWarning: function (elementId, message) {
        this.cleanMode();
        this.span.addClass("label-warning");
        this.span.text(message);
        ErrorMessage.span.show();
        this.scheduleHide();
        if (validValue(elementId)) {
            this.blinkElement(elementId);
        }
    },
    scheduleHide: function () {
        var uuid = guid();
        ErrorMessage.currentMessageId = uuid;
        setTimeout(function() {
            if (ErrorMessage.currentMessageId === uuid) {
                ErrorMessage.span.hide();
            }
        },4000);
    },
    blinkElement: function (elementId) {
        var element = $(elementId);
        var parent = element.parents().filter(function() {
            return $(this).css('position') == 'relative';
        }).first();

        parent.animate({
            scrollTop: element.offset().top-parent.offset().top
        }, 1000)
            .queue(function() {
                $( this ).dequeue();
                element.fadeIn(100).fadeOut(100).fadeIn(100).fadeOut(100).fadeIn(100);
            });
    }
};
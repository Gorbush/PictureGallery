function initProgressBar(barContentHolder, valueClass, progressClass) {
    var progressBarController = {
        root: $(barContentHolder),
        max : null
    };
    progressBarController.root.addClass("ProgressBar");
    progressBarController.root.append("<div class='ProgressValue'></div>");
    progressBarController.root.append("<div class='ProgressText'></div>");
    progressBarController.progressValue = progressBarController.root.find(".ProgressValue");
    progressBarController.progressText = progressBarController.root.find(".ProgressText");
    progressBarController.progressValue.width("0%" );
    progressBarController.progressText.text("");

    if (typeof valueClass != 'undefined') {
        progressBarController.progressValue.addClass(valueClass);
    }

    if (typeof progressClass != 'undefined') {
        progressBarController.progressText.addClass(progressClass);
    }

    progressBarController.setProgress = function (value, max, text) {
        var perc = 0;
        if (max == 0) {
            progressBarController.progressValue.width(0);
        } else {
            if (validValue(max)) {
                progressBarController.max = max;
            }
            perc = value*100/progressBarController.max;
            progressBarController.progressValue.width(perc.toFixed()+"%" );
        }
        if (validValue(text)) {
            progressBarController.progressText.text(text);
        } else {
            progressBarController.progressText.text(perc.toFixed() + " %");
        }
    };

    return progressBarController;
}

var AjaxHelper = {

    errorHandler: function (xhr, ajaxOptions, thrownError) {
        var responseJSON = xhr.responseJSON;
        if (xhr.responseText && !responseJSON) {
            responseJSON = JSON.parse(xhr.responseText);
        }
        alert("GET call failed " + thrownError);
    },

    successHandler: function (response, callBackSuccess) {
        if (response.status !== "200") {
            alert("Find failed " + response.message);
        } else {
            if (callBackSuccess) {
                callBackSuccess(response);
            }
        }
    },

    runGET: function (url, callBackSuccess, callbackError) {
        $.ajax({
            type: "GET",
            url: url,
            success: function (response) {
                AjaxHelper.successHandler(response, callBackSuccess);
            },
            error: callbackError ? callbackError : AjaxHelper.errorHandler,
            dataType: "json",
            contentType: "application/json"
            // async: false
        });
    },

    runPUT: function (url, callBackSuccess, callbackError) {
        $.ajax({
            type: "PUT",
            url: url,
            success: function (response) {
                AjaxHelper.successHandler(response, callBackSuccess);
            },
            error: callbackError ? callbackError : AjaxHelper.errorHandler,
            dataType: "json",
            contentType: "application/json"
            // async: false
        });
    },

    runPOST: function (url, callBack, callbackError) {
        $.ajax({
            type: "POST",
            url: "/sources/find",
            data: JSON.stringify(criteria, null, 2),
            success: function (response) {
                AjaxHelper.successHandler(response, callBackSuccess);
            },
            error: callbackError ? callbackError : AjaxHelper.errorHandler,
            dataType: "json",
            contentType: "application/json"
            // async: false
        });
    }
};
var Processes = {

    init: function () {
        // $(document).on("click", ".sourceBlock .matchingProperties", function (e1, e2, e3) {
        //     SourceProperties.show();
        // });
        $("#btnRunImport").on("click", function(event) {
            Processes.runImport();
            return false;
        });
        $(".log-container").on({
            mouseenter: function (event) {
                $("div.log-content", event.target).show();
            },
            mouseleave: function (event) {
                if (event.target.className === "log-content") {
                    $(event.target).hide();
                } else {
                    $("div.log-content", event.target).hide();
                }
            }
        });
    },

    runImport : function () {
        AjaxHelper.runGET("importing/import", function (response) {
            alert("Import started in folder "+response.importFolder);
        });
    },

    restartProcess: function (element) {
        var rowid = $(element).attr("rowid");
        AjaxHelper.runGET("/processes/restart/"+rowid, function (response) {
            alert("Import restarted in folder  "+response.importFolder);
        });
    }
};

$(document).ready(function () {
    Processes.init();
});

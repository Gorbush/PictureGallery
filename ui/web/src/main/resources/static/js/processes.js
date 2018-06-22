var Processes = {

    init: function () {
        // $(document).on("click", ".sourceBlock .matchingProperties", function (e1, e2, e3) {
        //     SourceProperties.show();
        // });
        $("#btnRunImport").on("click", function(event) {
            Processes.runImport();
            return false;
        });
        LogContainer.init();
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
    },

    openDetails: function (element) {
        var rowid = $(element).attr("rowid");
        window.open("/importProgress/"+rowid, "_blank");
    }
};

$(document).ready(function () {
    Processes.init();
});

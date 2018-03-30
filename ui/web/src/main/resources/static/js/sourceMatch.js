$(document).ready(function () {
    ErrorMessage.span = $("#message-label");
    ErrorMessage.hideMessage();

    ContextMenuFolderAction.initContextMenu("div#matchedSources div.filePath");

    $("div#pageMainButtons button").on('click', function () {

    });
    $("button#match").on('click', function () {
        sourceId = QueryString.sourceId;
        $.ajax({
            type: "GET",
            url: "/sources/match/"+sourceId,
            success: function (data) {
                if (data.status != "200") {
                    alert("ReIndex failed " + data.message);
                } else {
                    populateReportInfo(data.report);
                }
            },
            error: function (xhr, ajaxOptions, thrownError) {
                alert("ReIndex call failed " + thrownError);
            },
            dataType: "json",
            contentType: "application/json"
            // async: false
        });
    });
    $("button#apply").on('click', function () {
        getSourceDecisionsAndApply();
    });
    $("button#reset").on('click', function () {
        var parent = $("#matchedSources");
        $(".decisionButton", parent).removeClass(DecisionButtonBlock.BUTTON_SELECTED);

    });

});

function populateReportInfo(report) {
    var statSources = $("#stat_Sources");
    statSources.text(report.sources.length);
    var statPictures = $("#stat_Pictures");
    statPictures.text(report.pictures.length);

    var sourcesContent = $("div#matchedSources");
    sourcesContent.empty();

    var matchingContent = $("div#matchingSource");
    matchingContent.empty();

    SourceBlock.create(report.matchingSource, matchingContent, false, SourceList.gallery).hideDecisionButtons();
    SourceBlock.create(report.matchingSource, sourcesContent, fale, SourceList.gallery).showDecisionButtons();

    $.each(report.sources, function (indexSource, source) {
        SourceBlock.create(source, sourcesContent, false, SourceList.gallery).showDecisionButtons();
    });

    var picturesContent = $("div#matchedPictures");
    picturesContent.empty();
    $.each(report.pictures, function (indexPic, picture) {
        SourceBlock.create(picture, picturesContent, true, SourceList.gallery).hideDecisionButtons();
    });
}
function getSourceDecisionsAndApply() {
    var sourcesContent = $("div#matchedSources").find("div.sourceBlock");
    var decisions = [];
    var errorFound = false;
    $.each(sourcesContent, function (i, item) {
        var source = SourceBlock.get(item);
        var decision = source.getDecision();
        if (!validValue(decision)) {
            errorFound = true;
            ErrorMessage.showError(source.sourceBlockElement, "Decision not made for this source");
            return false;
        }
        var decision = {
            kind: decision,
            operands: [ source.dataObject.id ]
        };
        decisions.push(decision);
    });
    if (errorFound) {
        return;
    }
    $.ajax({
        type: "POST",
        url: "/sources/match/decisions",
        data: JSON.stringify(decisions),
        success: function (data) {
            if (data.status != "200") {
                alert("Decision delivery call error " + data.message);
            } else {
                alert("Decision delivery call succeed " + data.message);
            }
        },
        error: function (xhr, ajaxOptions, thrownError) {
            alert("Decision delivery call failed " + thrownError);
        },
        dataType: "json",
        contentType: "application/json"
    });
}
function initFolderStatsRequest(sourceBlock) {
    $.ajax({
        type: "POST",
        url: "/sources/stats/",
        data: sourceBlock.dataObject.filePath,
        success: function (data) {
            if (data.status != "200") {
                sourceBlock.failFolderStats(data.message);
            } else {
                sourceBlock.applyFolderStats(data.stats);
            }
        },
        error: function (xhr, ajaxOptions, thrownError) {
            sourceBlock.errorFolderStats(thrownError);
        },
        dataType: "json",
        contentType: "application/json"
    });
}
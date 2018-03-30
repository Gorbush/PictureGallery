var SourceList = {
    currentPath: null,
    currentRangeStart: null,
    currentRangeEnd: null,
    pagerBar: null,
    breadcrumb: null,
    gallery: null,

    fillBreadcrumb: function (breadcrumbText){
        var crumbs = breadcrumbText.split("/");
        SourceList.breadcrumb.empty();
        for(index in crumbs) {
            var cr = crumbs[index];
            if (index === (crumbs.length-1)) {
                SourceList.breadcrumb.append("<li><a href='#'>" + cr + "</a></li>");
            } else {
                SourceList.breadcrumb.append("<li class='active'><a href='#'>" + cr + "</a></li>");
            }
        }
    },

    init : function () {
        SourceList.pagerBar = Pager.create($("#sourcesNav"), pagerChangeHandler);
        SourceList.breadcrumb = $("#breadcrumblist");

        SourceList.treeFolderPath = TreePath.create($('#folderTree'), clickFoldersTreeNode, prepareCriteriaPath);
        SourceList.treeDates = TreeDates.create($('#datesTree'), clickDatesTreeNode, prepareCriteriaCurrent);
        SourceList.gallery = new Gallery({
            pager: SourceList.pagerBar,
            elements: {
                slideshow: '#slideshow',
                currentImage: '#slideshow .current .image',
                currentCaption: '#slideshow .current .caption',
                thumbnailAnchor: 'a.source_anch',
                galleryParent: "#sources",
                galleryElement: "#sources .sourceBlock",
                previousAnchor: '#slideshow .previous a',
                nextAnchor: '#slideshow .next a',
                closeAnchor: '#slideshow .close a'
            }
        });
    }
};

$(document).ready(function () {
    ErrorMessage.span = $("#message-label");
    ErrorMessage.hideMessage();
    $('.input-daterange').datepicker({
        format: "yyyy-mm-dd",
        autoclose: true,
        todayHighlight: true
    });

    SourceList.init();

    $("#refresh").on('click', function () {
        SourceList.treeFolderPath.refresh();
    });
});

function prepareCriteriaPath (treeElement, node) {
    var path = (node.id === "#") ? "/" : (node.id+"/");
    return prepareCriteria(path);
}
function prepareCriteriaCurrent (treeElement, node) {
    return prepareCriteria();
}
function prepareCriteria(path) {
    var starting = $("#datepicker input[name='start']").val();
    var ending = $("#datepicker input[name='end']").val();

    if (SourceList.treeDates) {
        var selectedDate = SourceList.treeDates.getCurrent();
        if (selectedDate) {
            starting = selectedDate.original.rangeStart;
            ending = selectedDate.original.rangeEnd;
        }
    }

    var sourcePath = "*";
    if (validValue(path)) {
        sourcePath = path;
    } else {
        if (SourceList.treeFolderPath) {
            var pathNode = SourceList.treeFolderPath.getCurrent();
            if (validValue(pathNode)) {
                sourcePath = pathNode.id;
            }
        }
    }

    return {
        path: sourcePath,
        fromDate: starting,
        toDate: ending,
        fileName: null,
        timestamp: null,
        placePath: null,
        page: 0,
        size: 1000
    };
}
function clickFoldersTreeNode(e, data, tree) {
    refreshSources(0);
    SourceList.treeDates.refresh();
}
function clickDatesTreeNode(e, data, tree) {
    refreshSources(0);
}
function refreshSources(page) {
    SourceList.pagerBar.updatePage(page);

    var container = $("div#sources");
    BLOCK_HEIGHT=100;
    BLOCK_WIDTH =350;
    var pageSize = Math.trunc(container.innerWidth() / BLOCK_WIDTH ) *
        (Math.trunc(container.innerHeight() / BLOCK_HEIGHT));

    var criteria = prepareCriteria();

    SourceList.currentPath = criteria.path;
    SourceList.currentRangeStart = criteria.fromDate;
    SourceList.currentRangeEnd = criteria.toDate;

    criteria.page = page;
    criteria.size = pageSize;

    $.ajax({
        type: "POST",
        url: "/sources/find",
        data: JSON.stringify(criteria, null, 2),
        success: function (response) {
            if (response.status != "200") {
                alert("Find failed " + response.message);
            } else {
                populateSourcesList(response, response.node);
            }
        },
        error: function (xhr, ajaxOptions, thrownError) {
            alert("ReIndex call failed " + thrownError);
        },
        dataType: "json",
        contentType: "application/json"
        // async: false
    });
}
function populateSourcesList(data, node) {
    var sourcesContent = $("div#sources");
    sourcesContent.empty();
    SourceList.fillBreadcrumb(data.criteria.path);
    $.each(data.list.content, function (indexSource, source) {
        SourceBlock.create(source, sourcesContent, false, SourceList.gallery).hideDecisionButtons();
    });

    SourceList.pagerBar.updateTo(data.list.number, data.list.totalPages, data.list.size, data.list.totalElements);

    if (SourceList.gallery.isAwaitingElement() ) {
        SourceList.gallery.changeSlideToAwaiting();
    }
}
function pagerChangeHandler(page, totalPages, size, total, pager) {
    if (SourceList.currentPath) {
        refreshSources(page);
    }
}
var SourceList = {
    /* data from ajax response with list, criteria and paging information */
    responseData: null,
    currentPath: null,
    currentRangeStart: null,
    currentRangeEnd: null,
    sourcesRootDiv: null,
    sourceDataProvider: "/sources/find",
    kind: "GALLERY",

    /** Linkable components */
    pagerBar: null,
    breadcrumb: null,
    gallery: null,
    viewSwitcher: null,

    currentView: null,
    VIEW_COMPACT: "compact",
    VIEW_LARGE: "large",
    CLASS_COMPACT: "compact",

    refreshSources: function (page) {
        refreshSources(page);
    },
    setView: function (viewName) {
        if (SourceList.currentView === viewName) {
            return;
        }
        SourceList.currentView = viewName;
        if (SourceList.VIEW_COMPACT === viewName) {
            SourceList.sourcesRootDiv.addClass(SourceList.CLASS_COMPACT);
        } else {
            SourceList.sourcesRootDiv.removeClass(SourceList.CLASS_COMPACT);
        }
        SourceList.refreshSources();
    },
    clean: function () {
        SourceList.sourcesRootDiv.empty();
        SourceList.responseData = null;
    },
    populate: function (data) {
        SourceList.responseData = data;
        SourceList.fillBreadcrumb(data.criteria.path);
        $.each(data.list.content, function (indexSource, source) {
            SourceBlock.create(source, SourceList.sourcesRootDiv, false, SourceList.gallery).hideDecisionButtons();
        });

        SourceList.pagerBar.updateTo(data.list.number, data.list.totalPages, data.list.size, data.list.totalElements);

        if (SourceList.gallery) {
            if (SourceList.gallery.isAwaitingElement() ) {
                SourceList.gallery.changeSlideToAwaiting();
            }
        }
    },
    get: function (index) {
        if (SourceList.responseData &&
            SourceList.responseData.list &&
            SourceList.responseData.list.content &&
            SourceList.responseData.list.content.length &&
            SourceList.responseData.list.content.length > index) {
            return SourceList.responseData.list.content[index];
        }
        return null;
    },
    getById: function (id) {
        if (SourceList.responseData &&
            SourceList.responseData.list &&
            SourceList.responseData.list.content &&
            SourceList.responseData.list.content.length) {
            var list = SourceList.responseData.list.content;
            for (var i = 0; i < list.length; i++) {
                if (list[i].id === id) {
                    return list[i];
                }
            }
        }
        return null;
    },
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

    init : function (sourceDataProvider, kind, criteriaContributor, viewSwitcher) {
        try {
            SourceList.sourceDataProvider = sourceDataProvider;
            SourceList.viewSwitcher = viewSwitcher;
            if (SourceList.viewSwitcher) {
                SourceList.viewSwitcher.setView(SourceList.viewSwitcher.COMPACT);
            }
            if (kind) {
                SourceList.kind = kind;
            }
            if (criteriaContributor) {
                SourceList.criteriaContributor = criteriaContributor;
            }
            SourceList.sourcesRootDiv = $("div#sources");
            SourceList.responseData = null;
            SourceList.pagerBar = Pager.create($("#sourcesNav"), pagerChangeHandler);
            SourceList.breadcrumb = $("#breadcrumblist");

            if (typeof TreePath != "undefined") {
                SourceList.treeFolderPath = TreePath.create($('#folderTree'), clickFoldersTreeNode, prepareCriteriaPath);
                SourceList.treeDates = TreeDates.create($('#datesTree'), clickDatesTreeNode, prepareCriteriaCurrent);
            }
            if (typeof Gallery != "undefined") {
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

            $("body").on({
                mouseenter: function () {
                    var stamp = $(".matchingProperties", this);
                    stamp.show();
                },
                mouseleave: function () {
                    var stamp = $(".matchingProperties", this);
                    stamp.hide();
                }
            },"div.SourceBlockContainer.compact div.sourceBlock");

        } catch (e) {
            console.log("Failed to init SourceList ");
        }
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

    $("#refresh").on('click', function () {
        if (SourceList.treeFolderPath) {
            SourceList.treeFolderPath.refresh();
        }
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

    var criteria = {
        path: "*",
        kind: SourceList.kind,
        fromDate: starting,
        toDate: ending,
        fileName: null,
        timestamp: null,
        placePath: null,
        page: 0,
        size: 1000
    };

    if (validValue(path)) {
        criteria.path = path;
    } else {
        if (SourceList.treeFolderPath) {
            var pathNode = SourceList.treeFolderPath.getCurrent();
            if (validValue(pathNode)) {
                criteria.path = pathNode.id;
            }
        }
    }

    if (SourceList.criteriaContributor) {
        criteria = SourceList.criteriaContributor(this, criteria);
    }

    return criteria;
}
function clickFoldersTreeNode(e, data, tree) {
    refreshSources(0);
    if (SourceList.treeDates) {
        SourceList.treeDates.refresh();
    }
}
function clickDatesTreeNode(e, data, tree) {
    refreshSources(0);
}
function refreshSources(page) {
    SourceProperties.hide();
    SourceList.pagerBar.updatePage(page);

    var container = $("div#sources");
    BLOCK_HEIGHT=100;
    BLOCK_WIDTH =350;
    var pageSize = Math.trunc(container.innerWidth() / BLOCK_WIDTH ) *
        (Math.trunc(container.innerHeight() / BLOCK_HEIGHT));
    if (pageSize < 1) {
        pageSize = 1;
    }
    var criteria = prepareCriteria();

    SourceList.currentPath = criteria.path;
    SourceList.currentRangeStart = criteria.fromDate;
    SourceList.currentRangeEnd = criteria.toDate;

    if (!validValue(page)) {
        page = 0;
    }
    criteria.page = page;
    criteria.size = pageSize;
    console.log("Reload sources path="+criteria.path+" requestId="+criteria.requestId);
    $.ajax({
        type: "POST",
        url: SourceList.sourceDataProvider,
        data: JSON.stringify(criteria, null, 2),
        success: function (response) {
            console.log("Reload success for path="+response.criteria.path+" requestId="+response.criteria.requestId);
            var list = response.list;
            console.log(" stats: page=("+list.number+" of "+list.totalPages+ ") elements=("+list.numberOfElements +" of " +list.size+") " +
                    " total="+list.totalElements);
            if (response.status != "200") {
                alert("Find failed " + response.message);
            } else {
                populateSourcesList(response, response.node);
            }
        },
        error: function (xhr, ajaxOptions, thrownError) {
            console.log("Reload failed for path="+response.criteria.path+" requestId="+response.criteria.requestId);
            alert("ReIndex call failed " + thrownError);
        },
        dataType: "json",
        contentType: "application/json"
        // async: false
    });
}
function populateSourcesList(data, node) {
    SourceList.clean();
    SourceList.populate(data);
}
function pagerChangeHandler(page, totalPages, size, total, pager) {
    if (SourceList.currentPath) {
        refreshSources(page);
    }
}
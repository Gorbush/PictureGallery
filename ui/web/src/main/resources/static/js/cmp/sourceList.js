var SourceList = {
    VIEW_COMPACT: "compact",
    VIEW_LARGE: "large",
    CLASS_COMPACT: "compact",

    init: function (optionsProvided) {
        try {
            var object = {
                BLOCK_WIDTH : 350,
                BLOCK_HEIGHT: 100,

                /* data from ajax response with list, criteria and paging information */
                responseData: null,
                currentPath: null,
                currentRangeStart: null,
                currentRangeEnd: null,
                sourcesRootDiv: null,
                sourceDataProvider: "/sources/find",
                grade: "GALLERY",
                showDecisionBlock: false,

                /** Linkable components */
                pagerBar: null,
                breadcrumb: null,
                gallery: null,
                viewSwitcher: null,

                currentView: null,

                initialized: false,

                loadedIdToBlocks: {},
                setView: function (viewName) {
                    if (this.currentView === viewName) {
                        return;
                    }
                    this.currentView = viewName;
                    if (SourceList.VIEW_COMPACT === viewName) {
                        this.sourcesRootDiv.addClass(SourceList.CLASS_COMPACT);
                    } else {
                        this.sourcesRootDiv.removeClass(SourceList.CLASS_COMPACT);
                    }
                    this.recalculateBlockSize();
                    this.refreshSources();
                },
                recalculateBlockSize: function () {
                    this.BLOCK_WIDTH = 350;
                    this.BLOCK_HEIGHT = 100;

                    var firstChild = this.sourcesRootDiv.find(".sourceBlock:first");
                    var firstChildNode;
                    if (firstChild.length > 0) {
                        firstChildNode = firstChild.get(0);
                        this.BLOCK_HEIGHT = firstChildNode.clientHeight;
                        this.BLOCK_WIDTH = firstChildNode.clientWidth;
                    } else {
                        firstChild = SourceBlock.create({}, this.sourcesRootDiv, false, null, null, this);
                        firstChild = firstChild.sourceBlockElement;
                        firstChildNode = firstChild.get(0);
                        this.BLOCK_HEIGHT = firstChildNode.clientHeight;
                        this.BLOCK_WIDTH = firstChildNode.clientWidth;
                        firstChild.remove();
                    }
                },
                clean: function () {
                    this.loadedIdToBlocks = {};
                    this.sourcesRootDiv.empty();
                    this.responseData = null;
                },
                populate: function (data) {
                    this.responseData = data;
                    this.fillBreadcrumb(data.criteria.path);
                    var sourceList = this;
                    $.each(data.list.content, function (indexSource, source) {
                        var block = SourceBlock.create(source, sourceList.sourcesRootDiv, false, sourceList.gallery, sourceList.options.onClick, sourceList);
                        if (!sourceList.showDecisionBlock) {
                            block.hideDecisionButtons();
                        } else {
                            block.markDecision(source.grade, source.status);
                        }
                    });

                    this.pagerBar.updateTo(data.list.number, data.list.totalPages, data.list.size, data.list.totalElements);

                    if (this.gallery) {
                        if (this.gallery.isAwaitingElement() ) {
                            this.gallery.changeSlideToAwaiting();
                        }
                    }
                },
                get: function (index) {
                    if (this.responseData &&
                        this.responseData.list &&
                        this.responseData.list.content &&
                        this.responseData.list.content.length &&
                        this.responseData.list.content.length > index) {
                        return this.responseData.list.content[index];
                    }
                    return null;
                },
                getBlockById : function (id) {
                    return this.loadedIdToBlocks[id];
                },
                getById: function (id) {
                    if (this.responseData &&
                        this.responseData.list &&
                        this.responseData.list.content &&
                        this.responseData.list.content.length) {
                        var list = this.responseData.list.content;
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
                    this.breadcrumb.empty();
                    for(index in crumbs) {
                        var cr = crumbs[index];
                        if (index === (crumbs.length-1)) {
                            this.breadcrumb.append("<li><a href='#'>" + cr + "</a></li>");
                        } else {
                            this.breadcrumb.append("<li class='active'><a href='#'>" + cr + "</a></li>");
                        }
                    }
                },

                prepareCriteria: function (path) {
                    var criteria = {
                        path: "",
                        grade: this.grade,
                        fileName: null,
                        timestamp: null,
                        placePath: null,
                        page: 0,
                        size: 1000
                    };

                    // if (validValue(path)) {
                    //     criteria.path = path;
                    // }

                    if (this.criteriaContributor) {
                        criteria = this.criteriaContributor(this, criteria);
                    }

                    return criteria;
                },

                clickFoldersTreeNode: function (e, data, tree) {
                    this.refreshSources(0);
                    if (this.treeDates) {
                        this.treeDates.refresh();
                    }
                },

                clickDatesTreeNode: function (e, data, tree) {
                    this.refreshSources(0);
                },

                populateSourcesList: function (data, node) {
                    this.clean();
                    this.populate(data);
                },

                pagerChangeHandler: function (page, totalPages, size, total, pager) {
                    if (this.initialized) {
                        this.refreshSources(page);
                    }
                },

                refreshSources: function (page) {
                    SourceProperties.hide();
                    this.pagerBar.updatePage(page);

                    var pageSize = Math.trunc(this.sourcesRootDiv.innerWidth() / this.BLOCK_WIDTH ) *
                        (Math.trunc(this.sourcesRootDiv.innerHeight() / this.BLOCK_HEIGHT));
                    if (pageSize < 1) {
                        pageSize = 1;
                    }
                    var criteria = this.prepareCriteria();

                    this.currentPath = criteria.path;
                    this.currentRangeStart = criteria.fromDate;
                    this.currentRangeEnd = criteria.toDate;

                    if (!validValue(page)) {
                        page = 0;
                    }
                    criteria.page = page;
                    criteria.size = pageSize;
                    console.log("Reload sources path="+criteria.path+" requestId="+criteria.requestId);
                    var object = this;
                    $.ajax({
                        type: "POST",
                        url: this.sourceDataProvider,
                        data: JSON.stringify(criteria, null, 2),
                        success: function (response) {
                            console.log("Reload success for path="+response.criteria.path+" requestId="+response.criteria.requestId);
                            var list = response.list;
                            console.log(" stats: page=("+list.number+" of "+list.totalPages+ ") elements=("+list.numberOfElements +" of " +list.size+") " +
                                " total="+list.totalElements);
                            if (response.status != "200") {
                                alert("Find failed " + response.message);
                            } else {
                                object.populateSourcesList(response, response.node);
                            }
                        },
                        error: function (xhr, ajaxOptions, thrownError) {
                            console.log("Reload failed Message: "+ xhr.message);
                            alert("ReIndex call failed because of " + xhr.message);
                        },
                        dataType: "json",
                        contentType: "application/json"
                        // async: false
                    });
                },
            };
            var defaultOptions = {
                sourceDataProvider: "/sources/uni",
                breadcrumb: "#breadcrumblist",
                // gallery: '#slideshow',
                // treePath: '#folderTree',
                // treeDates: '#datesTree',
                grade: "GALLERY",
                pagerBar: "#sourcesNav",
                sourcesRootDiv: "div#sources",
                criteriaContributor: null,
                viewSwitcher: "IMPORT",
                showDecisionBlock: false,
                onClick: null
            };
            object.options = $.extend(defaultOptions, optionsProvided);
            object.sourceDataProvider = object.options.sourceDataProvider;

            object.viewSwitcher = object.options.viewSwitcher;
            object.showDecisionBlock = object.options.showDecisionBlock;
            if (object.viewSwitcher) {
                object.viewSwitcher.setView(object.viewSwitcher.COMPACT);
            }

            object.grade = object.options.grade;
            object.criteriaContributor = object.options.criteriaContributor;
            object.sourcesRootDiv = $(object.options.sourcesRootDiv);
            object.pagerBar = Pager.create($(object.options.pagerBar), this.pagerChangeHandler);
            object.breadcrumb = $(object.options.breadcrumb);

            object.responseData = null;

            if (typeof TreePath != "undefined" && object.options.treePath) {
                object.treeFolderPath = TreePath.create($('#folderTree'),
                    function(e, data, tree) {return object.clickFoldersTreeNode(e, data, tree);},
                    function (treeElement, node) { return object.prepareCriteria(node);}
                    );
            }
            if (typeof TreePath != "undefined" && object.options.treeDates) {
                object.treeDates = TreeDates.create($('#datesTree'),
                    function(e, data, tree) {object.clickDatesTreeNode(e, data, tree);},
                    function (treeElement, node) { return object.prepareCriteria(node);}
                    );
            }
            if (typeof Gallery != "undefined" && object.options.gallery) {
                object.gallery = new Gallery({
                    pager: object.pagerBar,
                    elements: {
                        slideshow: object.options.gallery,
                        currentImage: object.options.gallery+' .current .image',
                        currentCaption: object.options.gallery+' .current .caption',
                        thumbnailAnchor: 'a.source_anch',
                        galleryParent: "#sources",
                        galleryElement: "#sources .sourceBlock",
                        previousAnchor: object.options.gallery+' .previous a',
                        nextAnchor: object.options.gallery+' .next a',
                        closeAnchor: object.options.gallery+' .close a'
                    }
                });
            }
            object.initialized = true;
            return object;
        } catch (e) {
            console.log("Failed to init SourceList "+e);
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

});

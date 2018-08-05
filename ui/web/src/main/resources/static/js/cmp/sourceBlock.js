var ContextMenuFolderAction = {
    menuElement: null,
    targetElement: null,
    targetElementEvent: null,

    contextMenuFunction: function (e) {
        ContextMenuFolderAction.targetElementEvent = e;
        ContextMenuFolderAction.targetElement = e.currentTarget;
        ContextMenuFolderAction.menuElement.css({
            display: "block",
            left: e.pageX,
            top: e.pageY
        });
        return false;
    },
    initContextMenu: function (menuSelector) {
        $("body").on("contextmenu", menuSelector, ContextMenuFolderAction.contextMenuFunction);
    },
    init: function () {
        ContextMenuFolderAction.menuElement = $("#contextMenuFolderAction");

        ContextMenuFolderAction.menuElement.on("click", "a", function() {
            ContextMenuFolderAction.menuElement.hide();
        });

        $(document).keyup(function(e) {
            if (e.keyCode === 27) { // escape key maps to keycode `27`
                ContextMenuFolderAction.menuElement.hide();
            }
        });
        $(document).mousedown(function(e){
            if( e.button === 2 ) {
                ContextMenuFolderAction.menuElement.hide();
            }
            return true;
        });
    }
};
var DecisionButtonBlock = {
    BUTTON_SELECTED: "decision_selected",
    BUTTON_DONE: "decision_done",
    BUTTON_LATER: "later",
    BUTTON_APPROVE: "approve",
    BUTTON_DUPLICATE: "duplicate",

    create: function (sourceBlock) {
        var decisionBlockDiv= $("div.decisionBlock", sourceBlock.sourceBlockElement ? sourceBlock.sourceBlockElement : sourceBlock);
        var buttons         = $(".decisionButton", decisionBlockDiv);
        var buttonLater     = $(".btn_LATER", decisionBlockDiv);
        var buttonApprove   = $(".btn_APPROVE", decisionBlockDiv);
        var buttonDuplicate = $(".btn_DUPLICATE", decisionBlockDiv);
        var object = {
            sourceBlock: sourceBlock,
            decisionBlockDiv: decisionBlockDiv,
            buttons: buttons,
            buttonLater: buttonLater,
            buttonApprove: buttonApprove,
            buttonDuplicate: buttonDuplicate,
            hideDecisionButtonsRequest: false,

            create: function () {
                this.buttons.on('click', function () {
                    object.selectButton(this, true);
                });
                // this.buttonApprove.on("dblclick", function () {
                //     var button = this;
                //     var parent = $(sourceBlock.sourceBlockElement).parent(".SourceBlockContainer");
                //     $(".decisionButton", parent).removeClass(DecisionButtonBlock.BUTTON_SELECTED);
                //     $(".decisionButton.btn_DUPLICATE", parent).addClass(DecisionButtonBlock.BUTTON_SELECTED);
                //     object.selectButton(button);
                // });
            },
            resetButton: function () {
                this.buttons.removeClass(DecisionButtonBlock.BUTTON_SELECTED);
            },
            selectButton: function (button, fireEvent) {
                this.resetButton();
                if (validValue(button) && !(button === "")) {
                    if (typeof button === 'string' || button instanceof String) {
                        button = $(".btn_"+button.toLocaleUpperCase(), this.decisionBlockDiv)
                    } else {
                        button = $(button);
                    }
                    button.addClass(DecisionButtonBlock.BUTTON_SELECTED);

                    if (fireEvent) {
                        var action = button.get(0).getAttribute("value");
                        var itemId = this.sourceBlock.getId();
                        var itemData = this.sourceBlock.getData();
                        object.performAction(button, action, itemId, itemData);
                    }
                }
            },
            performAction: function (button, action, itemId, itemData) {
               var blockOriginal = this;
               AjaxHelper.runGET("/sources/approve/"+itemData.grade+"/"+itemId+"/"+action, function (response){
                   var block = blockOriginal.sourceBlock.sourceList.getBlockById(response.result.id);
                   if (block) {
                       block.markDecision(response.result.grade, response.result.status);
                       ImportRequestsTree.refresh();
                   }
               });
            },
            getSelectedButton: function () {
                var buttonSelected = $("."+DecisionButtonBlock.BUTTON_SELECTED,this.decisionBlockDiv);
                if (buttonSelected.size() > 0) {
                    return buttonSelected.attr("value").toUpperCase();
                }
                return null;
            },
            markDecision: function (grade, status) {
                this.resetButton();
                if (grade === "PICTURE" && status === "APPROVED") {
                    this.decisionBlockDiv.addClass("decision_done");
                    this.selectButton(this.buttonApprove);
                }
                if (grade === "IMPORT" && status === "APPROVED") {
                    this.decisionBlockDiv.addClass("decision_done");
                    this.selectButton(this.buttonApprove);
                }
                if (grade === "IMPORT" && status === "DUPLICATE") {
                    this.decisionBlockDiv.addClass("decision_done");
                    this.selectButton(this.buttonDuplicate);
                }
            },
            hideDecisionButtons: function (notMarkHidden) {
                if (notMarkHidden === false) {
                    this.hideDecisionButtonsRequest = true;
                }
                this.decisionBlockDiv.hide();
            },
            showDecisionButtons: function () {
                this.decisionBlockDiv.show();
            }

        };
        object.create();
        return object;
    }
};
var SourceBlock = {
    FOLDER_ERROR_CLASS: "folderError",
    FOLDER_FAIL_CLASS: "folderFail",
    FOLDER_NOT_EXISTS_CLASS: "folderNotExists",

    globalInit: function () {
        if (!window.sourceBlockInitialized) {
            window.sourceBlockInitialized = true;
            $("body").on({
                mouseenter: function () {
                    var stamp = $(".matchingProperties", this);
                    stamp.show();
                    var boundingRect = stamp.get(0).getBoundingClientRect();
                    var view = stamp.get(0).ownerDocument.defaultView;
                    if (boundingRect.width+boundingRect.x > view.innerWidth) {
                        stamp.addClass("leftSide");
                    } else {
                        stamp.removeClass("leftSide");
                    }
                    if (boundingRect.height+boundingRect.y > view.innerHeight) {
                        stamp.addClass("topSide");
                    } else {
                        stamp.removeClass("topSide");
                    }
                },
                mouseleave: function () {
                    var stamp = $(".matchingProperties", this);
                    stamp.hide();
                }
            }, "div.SourceBlockContainer.compact div.sourceBlock");
            $(document).on("click", ".sourceBlock .matchingProperties", function (e1, e2, e3) {
                SourceProperties.show();
            });
        }
    },
    get: function (element) {
        do {
            var controller = $(element).data("controller");
            if (validValue(controller)) {
                return controller;
            }
            element = $(element).parent();
        } while (validValue(element));
    },
    create: function (dataObject, targetContainer, showStats, gallery, onClick, sourceList) {
        var object = {
            sourceList: sourceList,
            gallery: gallery,
            showStats: validValue(showStats) && showStats,
            dataObject: dataObject,
            targetContainer: targetContainer,
            sourceBlockElement: null,
            matchingImageDiv: null,
            matchingImage: null,
            matchingImageAnch: null,
            status: null,
            fileName: null,
            filePath: null,
            fileSize: null,
            timeStamp: null,
            decisionButtons: null,

            onClick: onClick,

            folderStats: null,
            folderStatsMessage: null,
            folderStatsData: null,
            folderStatsDataFiles: null,
            folderStatsDataMatched: null,
            folderStatsDataMatchable: null,
            folderStatsDataDups: null,

            loading: false,

            clone: function () {
                var self = this;
                this.sourceBlockElement = getTemplateCopy("sourceBlock");
                this.sourceBlockElement.data("controller", this);
                this.targetContainer.append(this.sourceBlockElement);
                this.matchingImageDiv   = $("div.source_image", this.sourceBlockElement);
                this.matchingImage      = $("img", this.matchingImageDiv);
                this.matchingImageAnch  = $(".source_anch", this.matchingImageDiv);
                this.status             = $("div.status", this.sourceBlockElement);
                this.fileName           = $("div.fileName", this.sourceBlockElement);
                this.filePath           = $("div.filePath", this.sourceBlockElement);
                this.fileSize           = $("div.fileSize", this.sourceBlockElement);
                this.timeStamp          = $("div.timeStamp", this.sourceBlockElement);

                this.folderStats = $("div.folderStats", this.sourceBlockElement);
                if (!this.showStats) {
                    this.folderStats.hide();
                }
                this.folderStatsMessage         = $(".message", this.folderStats);
                this.folderStatsData            = $(".data", this.folderStats);
                this.folderStatsDataFiles       = $(".files", this.folderStatsData);
                this.folderStatsDataMatched     = $(".matched", this.folderStatsData);
                this.folderStatsDataMatchable   = $(".matchable", this.folderStatsData);
                this.folderStatsDataDups        = $(".duplicates", this.folderStatsData);

                this.decisionButtons = DecisionButtonBlock.create(this);
                this.decisionButtons.selectButton("");

                var block = this;
                this.matchingImageDiv.on("dblclick", function(event) {
                    if (self.gallery) {
                        self.gallery.startSlideshow(block.matchingImage);
                    }
                    return false;
                });
                this.matchingImageDiv.on("click", function(event) {
                    event.preventDefault();
                    if (self.onClick) {
                        self.onClick(block, self);
                    }
                    return false;
                });

            },
            getId: function () {
                return this.sourceBlockElement.attr("data:id");
            },
            getData: function () {
                return this.dataObject;
            },
            preload: function () {
                console.log("Preload data for object with id="+this.dataObject.id+" grade="+this.dataObject.grade);
                this.matchingImage.attr("src", "/images/ajax-arrows.gif");
                this.loading = true;
                var block = this;
                AjaxHelper.runGET("/sources/get/"+this.dataObject.grade+"/"+this.dataObject.id, function (response) {
                    console.log("Import loaded for id "+response.result.id);
                    var dataObject = response.result;
                    block.dataObject = dataObject;
                    try {
                        block.fill();
                    } catch (e) {
                        console.log("Failed to fill the source block "+e);
                    }
                });
            },
            hideInfoBoxes: function () {
                $("[name='matchPicCount']",this.sourceBlockElement).hide();
                $("[name='matchImpCount']",this.sourceBlockElement).hide();
                $(".matchInfoBlock",this.sourceBlockElement).hide();
                this.hideDecisionButtons(false);
            },
            fill: function () {
                if (!validValue(this.dataObject.id)) {
                    console.log("empty data object!");
                    this.hideInfoBoxes();
                    return;
                }
                if (validValue(this.dataObject.id) && !validValue(this.dataObject.fileName)) {
                    this.hideInfoBoxes();
                    this.preload();
                    return;
                }
                if (this.dataObject.status === "SKIPPED" || this.dataObject.status === "FAILED") {
                    this.hideInfoBoxes(true);
                    this.hideInfoBoxes();
                } else {
                    if (!this.hideDecisionButtonsRequest) {
                        this.showDecisionButtons();
                    }
                }
                this.loading = false;
                console.log("Fill data for object with id="+this.dataObject.id);
                this.sourceBlockElement.attr("data:id", this.dataObject.id);
                this.matchingImageDiv.attr("title", "id: "+this.dataObject.id);

                if (this.sourceList) {
                    this.sourceList.loadedIdToBlocks[this.dataObject.id] = this;
                }

                if (this.dataObject.thumbPath != null && validValue(this.dataObject.thumbPath)) {
                    this.matchingImage.attr("src", "/thumbs/"+encodeURIComponent(this.dataObject.thumbPath).replace(/%2F/g, "/"));
                } else { // encodeURIComponent
                    this.matchingImage.attr("src", "/images/document_image_cancel_32.png");
                }
                var fullImageSrc = this.dataObject.filePath+"/"+this.dataObject.fileName;
                fullImageSrc = encodeURIComponent(fullImageSrc).replace(/%2F/g, "/");
                // var fullImagePic = "/pics/"+this.dataObject.filePath+"/"+this.dataObject.fileName;
                var hrefPicture = "";
                if (this.dataObject.grade === "GALLERY") {
                    hrefPicture = "/pics/"+fullImageSrc;
                }
                if (this.dataObject.grade === "IMPORT") {
                    hrefPicture = "/srcs/"+fullImageSrc;
                }
                if (this.dataObject.grade === "SOURCE") {
                    hrefPicture = "/srcs/"+fullImageSrc;
                }
                this.matchingImageAnch.attr("href", hrefPicture);
                this.matchingImageAnch.attr("title", this.dataObject.label);
                // this.status.text(this.dataObject.status);
                // this.fileName.text(this.dataObject.fileName);
                // this.filePath.text(this.dataObject.filePath);
                // this.fileSize.text(fileSizeSI(this.dataObject.size));
                // var st = formatDate(this.dataObject.timestamp);
                // this.timeStamp.text(st);
                FormHelper.populate(this.sourceBlockElement , this.dataObject);

                if (this.dataObject.matchReport) {
                    var matchPicCount = 0;
                    $.each(this.dataObject.matchReport.pictures, function (indexSource, importIds) {
                        matchPicCount += importIds.length;
                    });
                    var matchImpCount = 0;
                    $.each(this.dataObject.matchReport.currentImport, function (indexSource, importIds) {
                        matchImpCount += importIds.length;
                    });
                    FormHelper.populateFromObject(this.sourceBlockElement, {
                        matchPicCount: matchPicCount,
                        matchImpCount: matchImpCount
                    });
                    $("[name='matchPicCount']", this.sourceBlockElement).toggle(matchPicCount > 0);
                    $("[name='matchImpCount']", this.sourceBlockElement).toggle(matchImpCount > 0);
                    $(".matchInfoBlock", this.sourceBlockElement).toggle((matchPicCount + matchImpCount) > 0);
                } else {
                    $(".matchInfoBlock", this.sourceBlockElement).hide();
                }

                if (this.showStats) {
                    initFolderStatsRequest(this);
                }
                this.markDecision(this.dataObject.grade, this.dataObject.status);
            },

            getDecision: function () {
                return this.decisionButtons.getSelectedButton();
            },

            markDecision: function (grade, status) {
                this.sourceBlockElement.get(0).setAttribute("status", status);
                this.sourceBlockElement.get(0).setAttribute("grade", grade);
                /*
                if (!validValue(this.dataObject.id)) {
                    this.sourceBlockElement.addClass("source-block-unsaved");
                }
                if (this.dataObject.kind === "UNSET") {
                    this.sourceBlockElement.addClass("source-block-unset");
                }
                if (this.dataObject.kind === "DUPLICATE") {
                    this.sourceBlockElement.addClass("source-block-duplicate");
                }
                if (this.dataObject.kind === "PRIMARY") {
                    this.sourceBlockElement.addClass("source-block-primary");
                }
                */
                this.decisionButtons.markDecision(grade, status);
                return false;
            },

            failFolderStats: function (message) {
                this.cleanFolderStats();
                this.folderStatsMessage.show();
                this.folderStatsMessage.addClass(SourceBlock.FOLDER_FAIL_CLASS);
                this.folderStatsMessage.text("Failed "+ message);
            },

            errorFolderStats: function (message) {
                this.cleanFolderStats();
                this.folderStatsMessage.show();
                this.folderStatsMessage.addClass(SourceBlock.FOLDER_ERROR_CLASS);
                this.folderStatsMessage.text("Error "+ message);
            },

            cleanFolderStats: function () {
                this.folderStatsData.hide();
                this.folderStatsMessage.hide();
                this.folderStatsMessage.text("");

                this.folderStatsDataFiles.text();
                this.folderStatsDataMatched.text();
                this.folderStatsDataMatchable.text();
                this.folderStatsDataDups.text();

                this.folderStatsMessage.removeClass(SourceBlock.FOLDER_ERROR_CLASS);
                this.folderStatsMessage.removeClass(SourceBlock.FOLDER_FAIL_CLASS);
                this.folderStatsData.removeClass(SourceBlock.FOLDER_NOT_EXISTS_CLASS);
            },

            applyFolderStats: function (stats) {
                this.cleanFolderStats();
                this.folderStatsData.show();

                this.folderStatsDataFiles.text(stats.files);
                this.folderStatsDataMatched.text(stats.filesMatched);
                this.folderStatsDataMatchable.text(stats.filesExistingNotMatched);
                this.folderStatsDataDups.text(stats.filesDuplicates);

                if (!stats.exists) {
                    this.folderStatsData.addClass(SourceBlock.FOLDER_NOT_EXISTS_CLASS);
                }
            },
            hideDecisionButtons: function () {
                this.decisionButtons.hideDecisionButtons();
                return this;
            },
            showDecisionButtons: function () {
                this.decisionButtons.showDecisionButtons();
                return this;
            }

        };
        object.clone();
        try {
            object.fill();
        } catch (e) {
            console.log("Failed to fill the source block "+e);
        }

        return object;
    }
};

$(document).ready(function () {
    ContextMenuFolderAction.init();
    SourceBlock.globalInit();
});

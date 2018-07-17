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
               AjaxHelper.runGET("/sources/approve/"+itemData.grade+"/"+itemId+"/"+action, function (response){
                   var block = SourceList.getBlockById(response.result.id);
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
            hideDecisionButtons: function () {
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
    create: function (dataObject, targetContainer, showStats, gallery) {
        var object = {
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

            folderStats: null,
            folderStatsMessage: null,
            folderStatsData: null,
            folderStatsDataFiles: null,
            folderStatsDataMatched: null,
            folderStatsDataMatchable: null,
            folderStatsDataDups: null,

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
                this.matchingImageDiv.on("click", function(event) {
                    if (self.gallery) {
                        self.gallery.startSlideshow(block.matchingImage);
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
            fill: function () {
                SourceList.loadedIdToBlocks[this.dataObject.id] = this;
                this.sourceBlockElement.attr("data:id", this.dataObject.id);
                this.matchingImageDiv.attr("title", "id: "+this.dataObject.id);
                if (this.dataObject.thumbPath != null && validValue(this.dataObject.thumbPath)) {
                    this.matchingImage.attr("src", "/thumbs/"+encodeURIComponent(this.dataObject.thumbPath).replace(/%2F/g, "/"));
                } else { // encodeURIComponent
                    this.matchingImage.attr("src", "/images/document_image_cancel_32.png");
                }
                var fullImageSrc = this.dataObject.filePath+"/"+this.dataObject.fileName;
                fullImageSrc = encodeURIComponent(fullImageSrc).replace(/%2F/g, "/");
                // var fullImagePic = "/pics/"+this.dataObject.filePath+"/"+this.dataObject.fileName;
                this.matchingImageAnch.attr("href", "/srcs/"+fullImageSrc);
                this.matchingImageAnch.attr("title", this.dataObject.label);
                this.status.text(this.dataObject.status);
                this.fileName.text(this.dataObject.fileName);
                this.filePath.text(this.dataObject.filePath);
                this.fileSize.text(fileSizeSI(this.dataObject.size));
                var st = formatDate(this.dataObject.timestamp);
                this.timeStamp.text(st);

                if (this.dataObject.status === "SKIPPED" || this.dataObject.status === "FAILED") {
                    this.hideDecisionButtons();
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
        object.fill();

        return object;
    }
};

$(document).ready(function () {
    ContextMenuFolderAction.init();
    SourceBlock.globalInit();
});

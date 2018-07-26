/**
 * Created by sergii_puliaiev on 6/17/17.
 */
var ImportRequestsTree = {
    tree: null,
    treeColumnsTemplate: null,

    init: function () {
        ImportRequestsTree.tree = $('#indexRequestsTree');
        ImportRequestsTree.treeColumnsTemplate = $("#importTreeRow");
        ImportRequestsTree.autoRefreshCheck = $("#autoRefreshCheck");
        ImportRequestsTree.autoRefreshCheck.on('click', ImportRequestsTree.checkAutoRefresh);
        ImportRequestsTree.progress = {
            filesDiv: $("#filesProgress"),
            folderDiv: $("#foldersProgress")
        };
        ImportRequestsTree.progress.files = initProgressBar(ImportRequestsTree.progress.filesDiv);
        ImportRequestsTree.progress.folders = initProgressBar(ImportRequestsTree.progress.folderDiv);
        ImportRequestsTree.progress.folders.setProgress(0,0,"");
        ImportRequestsTree.progress.files.setProgress(0,0,"");

        ImportRequestsTree.header = $("#indexRequestTreeHeaderRightColumns");
        var row = populateTemplate(ImportRequestsTree.treeColumnsTemplate, null, ImportRequestsTree.header);

        ImportRequestsTree.headerTotals = $("#indexRequestTreeHeaderTotalColumns");
        populateTemplate(ImportRequestsTree.treeColumnsTemplate, {}, ImportRequestsTree.headerTotals);

        ImportRequestsTree.viewSwitcher = initViewSwitcher("#sourceViewSwitcher");
        ImportRequestsTree.sourceList = SourceList.init({
                showDecisionBlock: true,
                sourceDataProvider: "/sources/uni",
                breadcrumb: "#breadcrumblist",
                gallery: '#slideshow',
                treePath: '#folderTree',
                treeDates: '#datesTree',
                pagerBar: "#sourcesNav",
                sourcesRootDiv: "div#sources",
                criteriaContributor: ImportRequestsTree.criteriaContributor,
                viewSwitcher: ImportRequestsTree.viewSwitcher,
                grade: "IMPORT",
                onClick: ImportRequestsTree.onClickImportBlock
            });
        ImportRequestsTree.viewSwitcher.setSourceList(ImportRequestsTree.sourceList);

        ImportRequestsTree.matchesBlock = $("div#sourcesMatches");
        ImportRequestsTree.matchesBlockTemplate = $("div#matchBlockTemplate");

        ImportRequestsTree.treeComponent =
        ImportRequestsTree.tree.jstree({
            'core': {
                'data' : {
                    "url" : function (node, cb, par2) {
                        var id;
                        if (node.id === "#") {
                            // id = ImportRequestsTree.getActiveImportId();
                            id = "";
                        } else {
                            id = node.original.content.id;
                        }
                        var processId = ImportRequestsTree.getActiveProcessId();
                        return "/importing/list/"+processId+"/"+id;
                    },
                    "postprocessor": function (node, data) {
                        var dataNew = ImportRequestsTree.preprocessAsNodes(data.list.content, data.result, node);
                        // dataNew.id = node.id;
                        // dataNew.parent = node.parent;
                        return dataNew;
                    },
                    "renderer" : function (node, obj, settings, jstree, document) {
                        populateTemplate(ImportRequestsTree.treeColumnsTemplate, obj.original.content, node.childNodes[1]);
                    }
                }
            },
            "plugins": ["contextmenu", "dnd", "wholerow"],
            "contextmenu": {
                "items": function ($node) {
                    return {
                        "Analyse": {
                            "label": "Analyse",
                            "action": function (obj) {
                                var ref = $.jstree.reference(obj.reference),
                                    sel = ref.get_selected();
                                if(!sel.length) { return false; }
                                ImportRequestsTree.analyseNode(ImportRequestsTree.tree.jstree(true), sel);
                            }
                        },
                        "ApproveAll": {
                            "label": "Approve pending",
                            "action": function (obj) {
                                var ref = $.jstree.reference(obj.reference),
                                    sel = ref.get_selected(),
                                    node = ref.get_node(sel);
                                if(!sel.length) { return false; }
                                ImportRequestsTree.approveNode(node.original.content, true, false);
                            }
                        },
                        "ReMatchAll": {
                            "label": "Re-match",
                            "action": function (obj) {
                                var ref = $.jstree.reference(obj.reference),
                                    sel = ref.get_selected(),
                                    node = ref.get_node(sel);
                                if(!sel.length) { return false; }
                                ImportRequestsTree.rematchNode(node.original.content, true, false);
                            }
                        },
                        "Refresh": {
                            "label": "Refresh",
                            "action": function (obj) {
                                var ref = $.jstree.reference(obj.reference),
                                    sel = ref.get_selected();
                                if(!sel.length) { return false; }
                                ImportRequestsTree.tree.jstree(true).refresh_node(sel);
                            }
                        }
                    };
                }
            }
        }).bind('create_node.jstree', function (e, data) {
            console.log('hi', data);
        }).on("select_node.jstree", ImportRequestsTree.onNodeClick);
    },

    preprocessAsNodes: function (nodesList, nodeParent, treeNode) {
        function prepareNode(nodeData) {
            var node = {
                text    : (nodeData.path === "") ? "Gallery Root" : nodeData.path,
                icon    : "NODE_STATUS_" + nodeData.status,
                parent  : nodeData.parent ? nodeData.parent : "#",
                content : nodeData,
                state   : { opened: false },
                children: nodeData.foldersCount > 0
            };
            if (node.rootPath && node.text.startsWith(nodeData.rootPath)) {
                node.text = node.text.substr(nodeData.rootPath.length);
            }
            if (node.text.startsWith("/")) {
                node.text = node.text.substr(1);
            }
            return node;
        }

        var nodes = [];
        for(nodexIndex in nodesList) {
            var node = nodesList[nodexIndex];
            nodes.push(prepareNode(node));
        }

        if (nodeParent) {
            var childrenLoadRequest = treeNode && treeNode.original && treeNode.original.content&&
                                        nodeParent.id === treeNode.original.content.id;
            if (childrenLoadRequest) {
                return nodes;
            }
            var parentNode = prepareNode(nodeParent);
            if (nodes.length > 0) {
                parentNode.children = nodes;
            }
            return parentNode;
        }
        return nodes;
    },

    analyseNode: function (tree, node) {
        alert("Analyse node "+node);
    },

    getActiveImportId: function () {
        var id = $("#activeDetailId").val();
        return id;
    },
    setActiveImportId: function (val) {
        $("#activeDetailId").val(val);
    },
    getActiveProcessId: function () {
        var id = $("#processId").val();
        return id;
    },
    getProcessStatus: function () {
        var status = $("[name='process.status']").text();
        return status;
    },
    isProgressFinished: function () {
        var status = ImportRequestsTree.getProcessStatus();
        return status == "FINISHED" || status == "ABANDONED" || status == "FAILED";
    },
    isAutoRefresh: function () {
        return ImportRequestsTree.autoRefreshCheck.prop('checked');
    },
    checkAutoRefresh: function () {
        var isFinished = ImportRequestsTree.isProgressFinished();
        if (isFinished) {
            ImportRequestsTree.disableAutoRefresh();
        } else {
            ImportRequestsTree.autoRefreshCheck.prop('checked', true);
            setTimeout(function () {
                if (ImportRequestsTree.isAutoRefresh()) {
                    ImportRequestsTree.refresh();
                    ImportRequestsTree.checkAutoRefresh();
                } else {
                    ImportRequestsTree.disableAutoRefresh();
                }
            } ,5000);
        }
    },
    disableAutoRefresh: function () {
        ImportRequestsTree.autoRefreshCheck.prop('checked', false);
    },
    updateProgress: function (stats) {
        ImportRequestsTree.progress.folders.setProgress(stats.foldersDone, stats.folders, stats.foldersDone + " of " + stats.folders);
        ImportRequestsTree.progress.files.setProgress(stats.files, stats.files >= 0 ? stats.files : 0,stats.files+" files");
    },
    refresh: function() {
        var id = ImportRequestsTree.getActiveProcessId();
        AjaxHelper.runGET("/processes/"+id,
            function (response) {
                ImportRequestsTree.updateTotals(response.result.lastDetail);
                FormHelper.populate($("#importDetailsListData"), response.result);
            }
        );

        var tree = ImportRequestsTree.tree.jstree(true);
        // var selectedNode = tree.get_selected();
        // tree.refresh_node(selectedNode);
        // tree.deselect_all();
        // tree.select_node("#");
        // tree = ImportRequestsTree.treeComponent;
        // var currentNode = tree._get_node(null, false);
        // var parentNode = tree._get_parent(currentNode);
        tree.refresh(false, false);
    },

    updateTotals: function (data) {
        ImportRequestsTree.headerTotals.empty();
        data.name = "";
        var row = populateTemplate(ImportRequestsTree.treeColumnsTemplate, data, ImportRequestsTree.headerTotals);

        ImportRequestsTree.updateProgress(data.totalStats);
    },

    onNodeClick: function (e, context) {
        var data = context.node.original.content;
        var importRequestId = data.id;
        console.log("Selected node "+importRequestId);
        ImportRequestsTree.setActiveImportId(importRequestId);
        ImportRequestsTree.sourceList.refreshSources(0);
    },

    criteriaContributor: function(sourceList, criteria) {
        criteria.requestId = ImportRequestsTree.getActiveImportId();
        criteria.path = null;
        return criteria;
    },
    criteriaContributorMatches: function(sourceList, criteria) {
        var block = ImportRequestsTree.getSelectedImportSource();
        if (block) {
            criteria.matchesOfImportId = block.dataobject.id;
            criteria.path = null;
            return criteria;
        } else {
            debugger;
            return null;
        }
    },

    approveNode: function (node, approveOnlyPending, approveSubNodes) {
        console.log("ApproveNode "+node.id);
        AjaxHelper.runGET("/importing/approveImport/"+node.id, function (response) {
            ImportRequestsTree.refresh();
        });
    },
    rematchNode: function (node, approveOnlyPending, approveSubNodes) {
        console.log("rematchNode "+node.id);
        AjaxHelper.runGET("/importing/rematchImport/"+node.id, function (response) {
            ImportRequestsTree.refresh();
        });
    },
    setSelectedImportSource: function (block){
        ImportRequestsTree.selectedImportSource = block;
    },
    getSelectedImportSource: function (block){
        return ImportRequestsTree.selectedImportSource;
    },
    onClickImportBlock: function (block) {
        ImportRequestsTree.setSelectedImportSource(block);
        ImportRequestsTree.refreshMatchingReport();
    },
    refreshMatchingReport: function () {
        ImportRequestsTree.matchesBlock.empty();
        var block = ImportRequestsTree.getSelectedImportSource();
        var report = block.dataObject.matchReport;
        var reportImports = report.currentImport;
        var reportPicture = report.pictures;

        $.each(reportImports, function (indexSource, source) {

        });
        $.each(reportPicture, function (indexSource, matchIds) {
            var matches = populateTemplate(ImportRequestsTree.matchesBlockTemplate,{ title: indexSource}, ImportRequestsTree.matchesBlock);
            $.each(matchIds, function (index, id) {
                console.log("preloading id="+id+" grade=GALLERY");
                var matchSources = matches.find(".SourceBlockContainer");
                var blockMapped = SourceBlock.create({ id: id, grade: "GALLERY"}, matchSources, false, null, null, null).hideDecisionButtons();
            });

        });


    }
};
$(document).ready(function() {
    LogContainer.init();

    ImportRequestsTree.init();

    $("#ReIndexButton").on('click', function () {
        alert('Call Reindex!!');
        $.ajax({
            type: "GET",
            url: "/importing/import",
            success: function(data) {
                if (data.status != "200") {
                    alert("ReIndex failed "+data.message);
                } else {
                    alert("ReIndex started");
                }
            },
            error: function (xhr, ajaxOptions, thrownError) {
                alert("ReIndex call failed "+thrownError);
            },
            dataType: "json",
            contentType: "application/json"
            // async: false
        });
    });

    $("#btnRunRefresh").on('click', function () {
        ImportRequestsTree.refresh();
    });
    $("#refresh").on('click', function () {
        debugger;
        if (ImportRequestsTree.sourceList.treeFolderPath) {
            ImportRequestsTree.sourceList.treeFolderPath.refresh();
        }
    });

});

function getIndexRequests(nodeId) {
    debugger;
    var response = "";
    var processId = ImportRequestsTree.getActiveProcessId();
    var url = "/importing/list/"+processId+"/"+(nodeId==="#"?"":nodeId);
    $.ajax({
        type: "GET",
        url: url,
        success: function(data) {
            if (data.status != "200") {
                alert("Request failed!");
            } else {
                response = data;
            }
        },
        error: function (xhr, ajaxOptions, thrownError) {
            alert(thrownError);
        },
        dataType: "json",
        contentType: "application/json",
        async: false
    });
    if (response.status === '200') {
        return ImportRequestsTree.preprocessAsNodes(response.list.content, response.result);
    }
    return [{"text" : "ERROR", "id" : "1", "children" : false}];
}
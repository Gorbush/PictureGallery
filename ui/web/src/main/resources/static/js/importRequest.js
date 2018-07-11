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
        SourceList.init({
                showDecisionBlock: true,
                sourceDataProvider: "/sources/uni",
                breadcrumb: "#breadcrumblist",
                pagerBar: "#sourcesNav",
                sourcesRootDiv: "div#sources",
                criteriaContributor: ImportRequestsTree.criteriaContributor,
                viewSwitcher: ImportRequestsTree.viewSwitcher,
                grade: "IMPORT"
            });
        ImportRequestsTree.viewSwitcher.setSourceList(SourceList);

        ImportRequestsTree.treeComponent =
        ImportRequestsTree.tree.jstree({
            'core': {
                'data' : {
                    "url" : function (node, cb, par2) {
                        var id;
                        if (node.id === "#") {
                            id = ImportRequestsTree.getActiveImportId();
                            // id = "";
                        } else {
                            id = node.data.nodeId;
                        }
                        var processId = ImportRequestsTree.getActiveProcessId();
                        return "/importing/list/"+processId+"/"+id;
                    },
                    "postprocessor": function (node, data, par2) {
                        return ImportRequestsTree.preprocessAsNodes(data.list.content, data.result);
                    },
                    "renderer" : function (node, obj, settings, jstree, document) {
                        var row = populateTemplate(ImportRequestsTree.treeColumnsTemplate, obj.data);
                        moveChildren(row[0], node.childNodes[1]);
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

    preprocessAsNodes: function (nodesList, nodeParent) {
        function prepareNode(node) {
            node.text = (node.path === "") ? "Gallery Root" : node.path;
            if (node.rootPath && node.text.startsWith(node.rootPath)) {
                node.text = node.text.substr(node.rootPath.length);
            }
            if (node.text.startsWith("/")) {
                node.text = node.text.substr(1);
            }
            node.icon = "NODE_STATUS_" + node.status;
            node.data = node;
            node.state = {
                opened: false
            };
            node.parent = node.id;
            if (node.parent === null) {
                node.parent = '#';
                // node.id = '#';
            } else {
                // node.parent = node.id;
            }
            if (node.parent === node.rootId) {
                node.parent = '#';
                node.id = '#';
            }
            node.nodeId = node.id;
            // delete node.id;
        }

        for(nodexIndex in nodesList) {
            var node = nodesList[nodexIndex];
            prepareNode(node);
        }

        if (nodeParent) {
            prepareNode(nodeParent);
            // nodesList.push(nodeParent);
            if (nodesList.length > 0) {
                nodeParent.children = nodesList;
            }
            return nodeParent;
        }
        return nodesList;
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
        ImportRequestsTree.progress.folders.setProgress(stats.folders, stats.foldersDone,
            stats.foldersDone + " of " + stats.folders);
        ImportRequestsTree.progress.files.setProgress(stats.files, 0,stats.files+" files");
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
        var data = context.node.data;
        var importRequestId = data.id;
        console.log("Selected node "+importRequestId);
        ImportRequestsTree.setActiveImportId(importRequestId);
        SourceList.refreshSources(0);
    },

    criteriaContributor: function(sourceList, criteria) {
        criteria.requestId = ImportRequestsTree.getActiveImportId();
        criteria.path = "";
        return criteria;
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
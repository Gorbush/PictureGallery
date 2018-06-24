/**
 * Created by sergii_puliaiev on 6/17/17.
 */
var ImportRequestsTree = {
    tree: null,
    treeColumnsTemplate: null,

    init: function () {
        ImportRequestsTree.tree = $('#indexRequestsTree');
        ImportRequestsTree.treeColumnsTemplate = $("#importProgressTreeColumns");
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

        ImportRequestsTree.tree.jstree({
            'core': {
                'data' : {
                    "url" : function (node, cb, par2) {
                        var id = node.id;
                        if (node.id === "#") {
                            id = ImportRequestsTree.getActiveImportId();
                        }
                        return "/importing/list/"+id;
                    },
                    "postprocessor": function (node, data, par2) {
                        return ImportRequestsTree.preprocessAsNodes(data.response.content);
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
        });
    },

    preprocessAsNodes: function (nodesList) {
        for(nodexIndex in nodesList) {
            var node = nodesList[nodexIndex];
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
            if (node.parent === null || node.parent === node.rootId) {
                node.parent = '#';
            }
            node.text += '<div class="node_postblock">';
            node.text += '  <div class="status" data-toggle="tooltip" ';
            if (node.updated) {
                var tooltip = formatDate(node.updated);
                node.text += ' tooltip="'+tooltip+'"';
            }
            node.text += '>' +node.status+'</div>';
            node.text += '  <div class="filesCount">';
            if (node.filesCount != null) {
                if (node.filesIgnoredCount != null && node.filesIgnoredCount > 0) {
                    node.text += '(' + node.filesIgnoredCount + ')';
                }
                node.text += ' ' + node.filesCount;
            }
            node.text += '  </div>';
            node.text += '  <div class="foldersCount">';
            if (node.foldersCount != null) {
                if (node.foldersCount > 0) {
                    node.text += node.foldersCount;
                    node.children = true;
                } else {
                    node.children = false;
                }
            } else {
                node.children = true;
            }
            node.text += '  </div>';
            node.text += '</div>';
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
        ImportRequestsTree.tree.jstree(true).refresh();
        var id = ImportRequestsTree.getActiveProcessId();
        AjaxHelper.runGET("/processes/"+id,
            function (response) {
                FormHelper.populate($("#importDetailsListData"), response.result);
                var stats = response.result.lastDetail.totalStats;
                ImportRequestsTree.updateProgress(stats);
            }
        );
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
    var response = "";
    var url = "/importing/list/"+(nodeId==="#"?"":nodeId);
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
        return ImportRequestsTree.preprocessAsNodes(response.response.content);
    }
    return [{"text" : "ERROR", "id" : "1", "children" : false}];
}
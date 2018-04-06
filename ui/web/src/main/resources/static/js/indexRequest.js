/**
 * Created by sergii_puliaiev on 6/17/17.
 */

$(document).ready(function() {
    $('#indexRequestsTree').jstree({
        'core': {
            'data' : {
                "url" : function (node, cb, par2) {
                    return "/indexing/list/"+node.id;
                },
                "postprocessor": function (node, data, par2) {
                    return preprocessAsNodes(data.response.content);
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
                            analyseNode($('#indexRequestsTree').jstree(true), sel);
                        }
                    },
                    "Refresh": {
                        "label": "Refresh",
                        "action": function (obj) {
                            var ref = $.jstree.reference(obj.reference),
                                sel = ref.get_selected();
                            if(!sel.length) { return false; }
                            $('#indexRequestsTree').jstree(true).refresh_node(sel);
                        }
                    }
                };
            }
        }
    }).bind('create_node.jstree', function (e, data) {
        console.log('hi', data);
    });

    $("#ReIndexButton").on('click', function () {
        alert('Call Reindex!!');
        $.ajax({
            type: "GET",
            url: "/indexing/index",
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
});

function getIndexRequests(nodeId) {
    var response = "";
    var url = "/indexing/list/"+(nodeId==="#"?"":nodeId);
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
        return preprocessAsNodes(response.response.content);
    }
    return [{"text" : "ERROR", "id" : "1", "children" : false}];
}
function preprocessAsNodes(nodesList) {
    for(nodexIndex in nodesList) {
        var node = nodesList[nodexIndex];
        node.text = (node.path === "") ? "Gallery Root" : node.path;
        node.icon = "NODE_STATUS_" + node.status;
        node.data = node;
        node.state = {
            opened: false
        };
        node.children = true;
        if (node.parent === null) {
            node.parent = '#';
        }
        node.text += '<div class="node_postblock">';
        if (node.filesCount != null) {
            node.text += 'Files <span class="badge">' + node.filesCount + '</span>';
        }
        if (node.filesIgnoredCount != null) {
            node.text += 'Skipped <span class="badge">' + node.filesIgnoredCount + '</span>';
        }
        if (node.foldersCount != null) {
            node.text += 'Folders <span class="badge">' + node.foldersCount + '</span>';
        }
        node.text += 'Status <span class="badge">'+node.status+'</span>';
        node.text += '</div>';
    }

    return nodesList;
}

function analyseNode(tree, node) {
    alert("Analyse node "+node);
}
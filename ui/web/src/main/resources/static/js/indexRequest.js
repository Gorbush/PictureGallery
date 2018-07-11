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
                    return preprocessAsNodes(data.list.content, data.result);
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
        return preprocessAsNodes(response.list.content, response.result);
    }
    return [{"text" : "ERROR", "id" : "1", "children" : false}];
}
function preprocessAsNodes(nodesList, node) {
    for(nodexIndex in nodesList) {
        var node = nodesList[nodexIndex];
        node.text = (node.path === "") ? "Gallery Root" : node.path;
        node.icon = "NODE_STATUS_" + node.status;
        node.data = node;
        node.state = {
            opened: false
        };
        if (node.parent === null) {
            node.parent = '#';
        }
        node.text += '<div class="node_postblock">';
        node.text += '  <div class="status">'+node.status+'</div>';
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
}

function analyseNode(tree, node) {
    alert("Analyse node "+node);
}
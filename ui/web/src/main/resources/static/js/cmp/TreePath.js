/**
 * Created by sergii_puliaiev on 7/7/17.
 */
var TreePath = {

    init: function(){
        TreePath.treePathColumnsTemplate = $("#TreePathRowTemplate");
    },

    create: function (element, clickJSTreeNode, prepareCriteria) {
        TreePath.init();

        function preprocessFoldersAsNodes(nodesList, nodeParent) {
            var nodes = [];
            for(nodexIndex in nodesList.content) {
                var data = nodesList.content[nodexIndex];
                var path = data.name;
                var node = {
                    text: path,
                    icon: "glyphicon glyphicon-folder-open",
                    children: data.foldersCount > 0,
                    state: {
                        opened : false
                    },
                    content: data
                };
                nodes.push(node);
            }
            if (nodeParent.id === "#") {
                return  {
                    parent: "#",
                    text: "Gallery Root",
                    icon: "glyphicon glyphicon-folder-open",
                    children: nodes,
                    state: {
                        opened : true
                    }
                }
            }
            return nodes;
        }

        var object = {
            div: element,
            clickJSTreeNode: clickJSTreeNode,
            currentNode: null,
            prepareCriteria: prepareCriteria,

            init: function () {
                var treeObject = this;
                this.div.jstree({
                    'core': {
                        'data' : {
                            type: "POST",
                            dataType: "json",
                            contentType: "application/json",
                            "url" : function (node, cb, par2) {
                                return "/sources/findPath";
                            },
                            "data" : function (node, cb, par2) {
                                var data = node;
                                if (node && node.original && node.original.content) {
                                    data = node.original.content;
                                }
                                var criteria = treeObject.prepareCriteria(data);
                                return JSON.stringify(criteria, null, 2);
                            },
                            "postprocessor": function (node, data, par2) {
                                return preprocessFoldersAsNodes(data.list, node);
                            },
                            "renderer" : function (node, obj, settings, jstree, document) {
                                populateTemplate(TreePath.treePathColumnsTemplate, obj.original.content, node.childNodes[1]);
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
                                        analyseNode(ref, sel);
                                    }
                                },
                                "Refresh": {
                                    "label": "Refresh",
                                    "action": function (obj) {
                                        var ref = $.jstree.reference(obj.reference),
                                            sel = ref.get_selected();
                                        if(!sel.length) { return false; }
                                        ref.refresh_node(sel);
                                    }
                                }
                            };
                        }
                    }
                });
                this.div.bind('create_node.jstree', function (e, data) {
                    console.log('hi', data);
                });
                this.div.on("select_node.jstree", function (e, node) {
                    treeObject.currentNode = node.node;
                    if (treeObject.clickJSTreeNode) {
                        treeObject.clickJSTreeNode(e, node, treeObject);
                    }
                });
            },
            getCurrent: function () {
                // var ref = $.jstree.reference(obj.reference),
                //     sel = ref.get_selected();
                // if(!sel.length) { return false; }
                return this.currentNode;
            },

            refresh: function () {
                this.div.jstree().refresh();
            },
            refreshNode: function (nodeId) {
                this.div.jstree().refresh_node(nodeId);
            }
        };

        object.init();
        return object;
    }

};




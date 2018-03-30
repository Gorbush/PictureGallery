/**
 * Created by sergii_puliaiev on 7/7/17.
 */
var TreePath = {

    create: function (element, clickJSTreeNode, prepareCriteria) {
        function preprocessFoldersAsNodes(nodesList, nodeParent) {
            var nodes = [];
            for(nodexIndex in nodesList.content) {
                var path = nodesList.content[nodexIndex];
                if (path.name) {
                    path = path.name;
                }
                var node = {
                    id: (nodesList.root ? nodesList.root : "") + path,
                    text: (path === "")? "Gallery Root" : path,
                    icon: "glyphicon glyphicon-folder-open",
                    children: true,
                    state: {
                        opened : false
                    },
                    data: path,
                    parent: nodeParent.id
                };
                nodes.push(node);
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
                                var criteria = prepareCriteria(this, node);
                                return JSON.stringify(criteria, null, 2);
                            },
                            "postprocessor": function (node, data, par2) {
                                return preprocessFoldersAsNodes(data.list, node);
                            }
                        }
                    },
                    "plugins": ["contextmenu", "dnd"],
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




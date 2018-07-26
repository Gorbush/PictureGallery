/**
 * Created by sergii_puliaiev on 7/7/17.
 */
var TreeDates = {

    create: function (element, clickJSTreeNode, prepareCriteria) {
        function preprocessDatesAsNodes (nodesList, nodeParent, treeObject) {
            var nodes = [];
            var nodesTree = {};
            var currentNode = treeObject.getCurrent();
            var currentFound = false;
            var allNode = {
                id: "all",
                text: "All",
                icon: "glyphicon glyphicon-folder-open",
                rangeStart: null,
                rangeEnd: null,
                state: {
                    opened : true
                },
                parent: '#'
            };
            nodes.push(allNode);

            for(nodexIndex in nodesList) {
                var year = nodesList[nodexIndex].byYear;
                var yearNode = nodesTree[year];
                if (!validValue(yearNode)) {
                    yearNode = {
                        id: year,
                        text: year,
                        icon: "glyphicon glyphicon-folder-open",
                        rangeStart: formatToDateDateObject(new Date(year, 0, 1)),
                        rangeEnd: lastDayOfMonth(year, 11, 0),
                        // children: [],
                        state: {
                            opened : false
                        },
                        parent: 'all'
                    };
                    nodesTree[year] = yearNode;
                    nodes.push(yearNode);
                    if (year == currentNode) {
                        currentFound = true;
                    }
                }
                var month = nodesList[nodexIndex].byMonth;
                var monthNode = nodesTree[year+"-"+month];
                if (!validValue(monthNode)) {
                    monthNode = {
                        id: year+"-"+month,
                        text: month,
                        icon: "glyphicon glyphicon-folder-open",
                        rangeStart: formatToDateDateObject(new Date(year, month-1, 1)),
                        rangeEnd: lastDayOfMonth(year, month-1, 0),
                        // children: [],
                        state: {
                            opened : false
                        },
                        parent: year
                    };
                    nodesTree[year+"-"+month] = monthNode;
                    // yearNode.children.push(monthNode);
                    // yearNode.children.push(year+"-"+month);
                    nodes.push(monthNode);
                    if (year+"-"+month == currentNode) {
                        currentFound = true;
                    }
                }

                var day = nodesList[nodexIndex].byDay;
                var dayNode = nodesTree[year+"-"+month+"-"+day];
                if (!validValue(dayNode)) {
                    dayNode = {
                        id: year+"-"+month+"-"+day,
                        text: day,
                        icon: "glyphicon glyphicon-folder-open",
                        rangeStart: formatToDateDateObject(new Date(year, month-1, day)),
                        rangeEnd: formatToDateDateObject(new Date(year, month-1, day)),
                        // children: false,
                        state: {
                            opened : false
                        },
                        parent: year+"-"+month
                    };
                    nodesTree[year+"-"+month+"-"+day] = dayNode;
                    // monthNode.children.push(dayNode);
                    // monthNode.children.push(year+"-"+month+"-"+day);
                    nodes.push(dayNode);
                    if (year+"-"+month+"-"+day == currentNode) {
                        currentFound = true;
                    }
                }
            }

            if (!currentFound) {
                treeObject.currentNode = null;
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
                                return "/sources/findDates";
                            },
                            "data" : function (node, cb, par2) {
                                var criteria = treeObject.prepareCriteria(node);
                                return JSON.stringify(criteria, null, 2);
                            },
                            "postprocessor": function (node, data, par2) {
                                return preprocessDatesAsNodes(data.list, node, treeObject);
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
                                        // ref.refresh_node(sel);//
                                        ref.refresh();//
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




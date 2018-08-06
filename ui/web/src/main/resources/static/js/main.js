var GalleryManager = {

    init: function () {
        GalleryManager.viewSwitcher = initViewSwitcher("#sourceViewSwitcher");
        GalleryManager.sourceList = SourceList.init({
            showDecisionBlock: false,
            sourceDataProvider: "/sources/uni",
            breadcrumb: "#breadcrumblist",
            gallery: '#slideshow',
            // treePath: TreePath.create("#folderTree", false, "#TreePathRowTemplate"),
            treePath: TreeFolders.create("#folderTree", false, "#TreePathRowTemplate"),
            treeDates: TreeDates.create('#datesTree', false, null),
            pagerBar: "#sourcesNav",
            sourcesRootDiv: "div#sources",
            criteriaContributor: GalleryManager.criteriaContributor,
            viewSwitcher: GalleryManager.viewSwitcher,
            grade: "GALLERY",
            onClick: GalleryManager.onClickImportBlock
        });
    },
    setSelectedPicture: function (block) {
        GalleryManager.selectedBlock = block;
    },
    getSelectedPicture: function () {
        return GalleryManager.selectedBlock;
    },
    onClickImportBlock: function (block) {
        GalleryManager.setSelectedPicture(block);
    },
    criteriaContributor: function(sourceList, criteria, nodeData, nodeType) {
        var starting = $("#datepicker input[name='start']").val();
        var ending = $("#datepicker input[name='end']").val();
        if (starting === "") {
            starting = null;
        }
        if (ending === "") {
            ending = null;
        }

        if (GalleryManager.sourceList && GalleryManager.sourceList.treeDates) {
            var selectedDate = GalleryManager.sourceList.treeDates.getCurrent();
            if (selectedDate) {
                starting = selectedDate.original.rangeStart;
                ending   = selectedDate.original.rangeEnd;
            }
        }
        if (starting) {
            criteria.fromDate   = starting;
        }
        if (ending) {
            criteria.toDate     = ending;
        }

        if (GalleryManager.sourceList && GalleryManager.sourceList.treePath) {
            if (validValue(nodeData) && nodeType === "path") {
                if (nodeData.parent === "#" || nodeData.id === "#") {
                    criteria.folderId = "";
                } else {
                    criteria.folderId = nodeData.id;
                }
                if (criteria.path === "") {
                    delete criteria.path;
                }
            } else {
                var pathNode = GalleryManager.sourceList.treePath.getCurrent();
                if (validValue(pathNode)) {
                    if (pathNode.parent === "#" || pathNode.id === "#") {
                        criteria.folderId = "";
                    } else {
                        criteria.folderId = pathNode.original.content.id;
                    }
                    if (criteria.path === "") {
                        delete criteria.path;
                    }
                } else {
                    debugger;
                    criteria.folderId = "";
                    if (criteria.path === "") {
                        delete criteria.path;
                    }
                }
            }
        }
        return criteria;
    },
};

$(document).ready(function() {
    GalleryManager.init();
});
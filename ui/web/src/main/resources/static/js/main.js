var GalleryManager = {

    init: function () {
        GalleryManager.viewSwitcher = initViewSwitcher("#sourceViewSwitcher");
        GalleryManager.sourceList = SourceList.init({
            showDecisionBlock: true,
            sourceDataProvider: "/sources/uni",
            breadcrumb: "#breadcrumblist",
            gallery: '#slideshow',
            treePath: '#folderTree',
            treeDates: '#datesTree',
            pagerBar: "#sourcesNav",
            sourcesRootDiv: "div#sources",
            criteriaContributor: GalleryManager.criteriaContributor,
            viewSwitcher: GalleryManager.viewSwitcher,
            grade: "GALLERY",
            onClick: GalleryManager.onClickImportBlock
        });
        GalleryManager.viewSwitcher.setSourceList(GalleryManager.sourceList);
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
    criteriaContributor: function(sourceList, criteria) {
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

        if (GalleryManager.sourceList && GalleryManager.sourceList.treeFolderPath) {
            var pathNode = GalleryManager.sourceList.treeFolderPath.getCurrent();
            if (validValue(pathNode) && !(pathNode.parent === "#")) {
                criteria.path = pathNode.original.content.fullPath;
            }
        }
        return criteria;
    },
};

$(document).ready(function() {
    GalleryManager.init();
});
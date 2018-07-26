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
        // criteria.requestId = GalleryManager.getSelectedPicture();
        criteria.path = "";
        return criteria;
    },
};

$(document).ready(function() {
    GalleryManager.init();
});
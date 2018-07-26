function initViewSwitcher (targetDiv) {
    var switcher = {
        COMPACT: "compact",
        LARGE: "large",

        targetDiv       : $(targetDiv),
        selectors       : $(".viewButton", targetDiv),
        selectorCompact : $(".viewButton.view-compact", targetDiv),
        selectorLarge   : $(".viewButton.view-large", targetDiv),
        sourceList      : null,

        currentView: null,
        helper: null,

        setView: function (viewName) {
            switcher.helper.setView(viewName);
        },

        setSourceList: function (sourceList) {
            switcher.sourceList = sourceList;
            switcher.helper.setView(switcher.COMPACT);
        }
    };

    var helperObject = {
        CLASS_COMPACT: "compact",
        CLASS_SELECTED: "selected",
        CLASS_DISABLED: "disabled",

        switcher: switcher,

        init: function () {
            this.setView(switcher.COMPACT);
            switcher.targetDiv.addClass("viewSwitcher");
            switcher.selectorCompact.on("click", function () {
                switcher.setView(switcher.COMPACT);
            });
            switcher.selectorLarge.on("click", function () {
                switcher.setView(switcher.LARGE);
            });
        },
        setView: function (viewName) {
            if (!switcher.sourceList) {
                switcher.selectors.removeClass(this.CLASS_SELECTED);
                switcher.selectors.addClass(this.CLASS_DISABLED);
                return;
            }
            switcher.selectors.removeClass(this.CLASS_DISABLED);
            if (switcher.currentView === viewName) {
                return;
            }
            switcher.selectors.removeClass(this.CLASS_SELECTED);
            switcher.currentView = viewName;
            if (switcher.COMPACT == viewName) {
                switcher.sourceList.setView(viewName);
                switcher.selectorCompact.addClass(this.CLASS_SELECTED);
            } else {
                switcher.sourceList.setView(viewName);
                switcher.selectorLarge.addClass(this.CLASS_SELECTED);
            }
        }

    };
    switcher.helper = helperObject;
    switcher.helper.init();

    return switcher;
}
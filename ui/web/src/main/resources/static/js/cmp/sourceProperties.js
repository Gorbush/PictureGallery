var SourceProperties = {
    ROOT_BLOCK: null,
    initialized: false,

    init: function (rootBlock) {
        this.ROOT_BLOCK = $(rootBlock);
        $("#sourcePropHeader .icon", this.ROOT_BLOCK).click(function (e1, e2, e3) {
            SourceProperties.hide();
        });
        this.initialized = true;
    },

    show : function () {
        if (this.ROOT_BLOCK) {
            this.ROOT_BLOCK.show();
        }
    },
    hide : function () {
        if (this.ROOT_BLOCK) {
            this.ROOT_BLOCK.hide();
        }
    }
};

$(document).ready(function () {
    SourceProperties.init("#sourceProperties");
});

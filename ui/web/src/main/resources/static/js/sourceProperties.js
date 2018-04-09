var SourceProperties = {
    ROOT_BLOCK: null,

    init: function (rootBlock) {
        this.ROOT_BLOCK = $(rootBlock);
        $("#sourcePropHeader .icon", this.ROOT_BLOCK).click(function (e1, e2, e3) {
            SourceProperties.hide();
        });

    },

    show : function () {
        this.ROOT_BLOCK.show();
    },
    hide : function () {
        this.ROOT_BLOCK.hide();
    }
};

$(document).ready(function () {
    SourceProperties.init("#sourceProperties");
});

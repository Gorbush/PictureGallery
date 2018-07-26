/**
 * Created by sergii_puliaiev on 7/5/17.
 */
var Pager = {

    create: function (navElement, onChangeHandler) {
        var elems = navElement.find("li");
        var object = {
            navElement: navElement,
            elems: elems,
            elFirst: $(elems.get(0)),
            elPrev: $(elems.get(1)),
            el1: $(elems.get(2)),
            el2: $(elems.get(3)),
            elMiddle1: $(elems.get(4)),
            elMiddleValue: $(elems.get(5)),
            elMiddle2: $(elems.get(6)),
            el3: $(elems.get(7)),
            el4: $(elems.get(8)),
            elNext: $(elems.get(9)),
            elLast: $(elems.get(10)),
            page: null,
            size: null,
            total: null,
            totalPages: null,
            onChangeHandler: onChangeHandler,

            nextPage: function () {
                this.updateTo(this.page+1, this.totalPages, this.size, this.total);
            },
            prevPage: function () {
                this.updateTo(this.page-1, this.totalPages, this.size, this.total);
            },
            updatePage: function (pageToSet) {
                this.updateTo(pageToSet, this.totalPages, this.size, this.total);
            },
            updateTo: function (page, totalPages, size, total) {
                page = (page < totalPages) ? page : totalPages-1;
                page = (page < 0) ? 0 : page;

                if (this.page === page && this.totalPages === totalPages
                    && this.size === size && this.total === total) {
                    return;
                }
                this.page = page;
                this.totalPages = totalPages;
                this.size = size;
                this.total = total;

                this.elems.removeClass("active");
                this.elems.show();
                this.el3.find("a").text(totalPages-1);
                this.el4.find("a").text(totalPages);
                if (page === 0) {
                    this.el1.addClass("active");
                }
                if (page === 1) {
                    this.el2.addClass("active");
                }
                if (page === totalPages-2) {
                    this.el3.addClass("active");
                }
                if (page === totalPages-1) {
                    this.el4.addClass("active");
                }
                // middle evaluation
                if (totalPages >= 6 && page > 1 && page < totalPages-2) {
                    this.elMiddleValue.show();
                    this.elMiddleValue.addClass("active");
                    this.elMiddleValue.find("a").text(page+1);

                    this.elMiddle1.toggle(page >= 3);
                    this.elMiddle2.toggle(totalPages > 6 && page <= totalPages-4);
                } else {
                    this.elMiddleValue.hide();
                    this.elMiddle2.hide();
                    this.elMiddle1.toggle(totalPages >= 6);
                }
                this.el2.toggle(totalPages > 2);
                this.el3.toggle(totalPages > 3);
                this.el4.toggle(totalPages > 1);

                this.elFirst.toggleClass("disabled", page === 0);
                this.elLast.toggleClass("disabled", page === totalPages-1);
                this.elPrev.toggleClass("disabled", page === 0);
                this.elNext.toggleClass("disabled", page === totalPages-1);

                if(this.onChangeHandler) {
                    onChangeHandler(this.page, this.totalPages, this.size, this.total, this);
                }
            },
            init: function () {
                this.updateTo(0,1,0,0);
                var pager = this;
                this.elPrev.on("click", function () {
                    pager.updateTo(pager.page-1, pager.totalPages, pager.size, pager.total);
                });
                this.elNext.on("click", function () {
                    pager.updateTo(pager.page+1, pager.totalPages, pager.size, pager.total);
                });
                this.elFirst.on("click", function () {
                    pager.updateTo(0, pager.totalPages, pager.size, pager.total);
                });
                this.elLast.on("click", function () {
                    pager.updateTo(pager.totalPages-1, pager.totalPages, pager.size, pager.total);
                });
                this.el1.on("click", function () {
                    pager.updateTo(0, pager.totalPages, pager.size, pager.total);
                });
                this.el2.on("click", function () {
                    pager.updateTo(1, pager.totalPages, pager.size, pager.total);
                });
                this.el3.on("click", function () {
                    pager.updateTo(pager.totalPages-2, pager.totalPages, pager.size, pager.total);
                });
                this.el4.on("click", function () {
                    pager.updateTo(pager.totalPages-1, pager.totalPages, pager.size, pager.total);
                });
                this.elMiddleValue.on("click", function () {
                    pager.updateTo(pager.page, pager.totalPages, pager.size, pager.total);
                });
            }
        };

        object.init();

        return object;
    }

};
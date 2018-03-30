/** http://www.jqueryscript.net/gallery/Handy-Slideshow-style-Image-Gallery-Plugin-For-jQuery-gallery-js.html */
function Gallery(configuration) {
	var self = this;

	self.root = $(configuration.elements.galleryParent);

    self.configuration = configuration;
    self.pager = configuration.pager;
    self.LAST_ELEMENT = -1;
	self.REQUEST_FIRST_ELEMENT = -2;
    self.REQUEST_LAST_ELEMENT = -3;
    self.currentSlide = {
		source: null,
		caption: null,
        index: null,
        element: null
	};

    self.isActive = function () {
        return $(configuration.elements.slideshow).is(":visible");
    };

    self.isAwaitingElement = function () {
        return self.isActive() && (
            self.currentSlide.index === self.REQUEST_FIRST_ELEMENT
            ||
            self.currentSlide.index === self.REQUEST_LAST_ELEMENT
        );
    };

	self.findElementByIndex = function (index) {
        var block = $(self.root.children().last());
        if (validValue(index)) {
            if (index === self.REQUEST_LAST_ELEMENT) {
                index = self.LAST_ELEMENT;
            }
            if (index === self.REQUEST_FIRST_ELEMENT) {
                index = 0;
            }
            if (index === self.LAST_ELEMENT) {
                var lastSlide = self.root.children().last().find(configuration.elements.thumbnailAnchor);
                var lastIndex = self.getElementIndex(lastSlide);
                index = lastIndex;
            }

            var selectedBlock = self.root.children()[index];
            if (selectedBlock) {
                block = $(selectedBlock);
            }
        }
        var slide = block.find(configuration.elements.thumbnailAnchor);
        return slide;
	};

	self.getElementIndex = function (slide) {
	    if (validValue(slide)) {
            if (!slide.parents) {
                slide = $(slide);
            }
            var block = slide.parents(self.configuration.elements.galleryElement);
            return self.root.children().index(block);
        } else {
	        debugger;
        }
	};

	self.bindEvents = function() {
	    if (!self.eventsBind) {
            self.eventsBind = true;
            $(configuration.elements.thumbnailAnchor).click(function (event) {
                self.startSlideshow(this);
                event.preventDefault();
            });
            $(configuration.elements.previousAnchor).click(function (event) {
                self.showPreviousSlide();
                event.preventDefault();
            });
            $(configuration.elements.nextAnchor).click(function (event) {
                self.showNextSlide();
                event.preventDefault();
            });
            $(configuration.elements.closeAnchor).click(function (event) {
                self.endSlideshow();
                event.preventDefault();
            });

            $(document).keydown(function (event) {
                if (event.shiftKey || event.altKey || event.ctrlKey || event.metaKey) {
                    return;
                }
                switch (event.which) {
                    case 13:
                        self.startSlideshow(null);
                        event.preventDefault();
                        return false;
                    case 37:
                        self.showPreviousSlide();
                        event.preventDefault();
                        return false;
                    case 39:
                        self.showNextSlide();
                        event.preventDefault();
                        return false;
                    case 27:
                        self.endSlideshow();
                        event.preventDefault();
                        return false;
                    case 82: // R  button for rotation right
                        self.applyRotationRight();
                        event.preventDefault();
                        return false;
                    case 76: // L  button for rotation left
                        self.applyRotationLeft();
                        event.preventDefault();
                        return false;
                    default:
                        return;
                }
            });
        }
	};
	
	self.startSlideshow = function(firstSlide) {
		self.changeSlide(firstSlide);

		$(configuration.elements.slideshow).show();
        self.bindEvents();
	};
	
	self.changeSlide = function(slideIdentifier) {
        self.removeRotation();
        if (slideIdentifier === null || !validValue(slideIdentifier)) {
            if (document.location.hash) {
                slideIdentifier = document.location.hash.slice(1);
            }
        }
        if (typeof slideIdentifier === "number") {
            self.currentSlide.index = slideIdentifier;
        }
        if (typeof slideIdentifier === "object") {
            self.currentSlide.index = self.getElementIndex(slideIdentifier);
        }
        if (!validValue(slideIdentifier)) {
            self.currentSlide.index = 0;
        }

        var slide = self.findElementByIndex(self.currentSlide.index);

        document.location.hash = self.currentSlide.index;
        self.currentSlide.element = $(slide);
		self.currentSlide.source = self.currentSlide.element.attr('href');
		self.currentSlide.caption = self.currentSlide.element.attr('title');

        if (self.currentSlide.source) {
            $(configuration.elements.currentImage).css({
                'background-image': "url("+self.currentSlide.source.replace(/ /g, "%20")+")"
            });
        }
        if (self.currentSlide.caption) {
            $(configuration.elements.currentCaption).html(self.currentSlide.caption);
        }

		var anch = $('a[href="' + self.currentSlide.source + '"]');
		var block = anch.parents(configuration.elements.galleryElement);
		self.changeHash(block.index()); // +1
	};
	
	self.changeHash = function(hash) {
		if (hash === null) {
			history.pushState('', document.title, window.location.pathname);
		} else {
			document.location.hash = hash;
		}
	};
	
	self.showPreviousSlide = function() {
		var currentSlide = $(configuration.elements.thumbnailAnchor + '[href="' + self.currentSlide.source + '"]');
		var previousSlide = currentSlide.parents(configuration.elements.galleryElement).prev()
            .find(configuration.elements.thumbnailAnchor).first();
        if ($(previousSlide).length === 0) {
            if (self.pager) {
                self.currentSlide.index = self.REQUEST_LAST_ELEMENT;
                self.pager.prevPage();
                return;
            } else {
                previousSlide = self.root.children().last().find(configuration.elements.thumbnailAnchor);
            }
        }
		self.changeSlide(previousSlide);
	};
	
	self.showNextSlide = function() {
        var currentSlide = $(configuration.elements.thumbnailAnchor + '[href="' + self.currentSlide.source + '"]');
        var nextSlide = currentSlide.parents(configuration.elements.galleryElement).next()
            .find(configuration.elements.thumbnailAnchor).first();
        if ($(nextSlide).length === 0) {
            // means this was the last
            if (self.pager) {
                self.currentSlide.index = self.REQUEST_FIRST_ELEMENT;
                self.pager.nextPage();
                return;
            } else {
                self.currentSlide.index = 0;
                nextSlide = self.root.children().first().find(configuration.elements.thumbnailAnchor);
            }
        }
        self.changeSlide(nextSlide);
    };

	self.changeSlideToAwaiting = function() {
	    if (self.isAwaitingElement()) {
            if (self.currentSlide.index === self.REQUEST_FIRST_ELEMENT ) {
                self.changeSlide(0);
            }
            if (self.currentSlide.index === self.REQUEST_LAST_ELEMENT ) {
                self.changeSlide(self.LAST_ELEMENT);
            }
        }

    };
	self.endSlideshow = function() {
        self.removeRotation();
		$(configuration.elements.slideshow).hide();
		self.changeHash(null);
	};
	self.applyRotationRight = function() {
        var imageFull = $(self.configuration.elements.currentImage);
        if (imageFull.hasClass("rotate90")) {
            imageFull.removeClass("rotate90");
            imageFull.addClass("rotate180");
        } else {
            if (imageFull.hasClass("rotate180")) {
                imageFull.removeClass("rotate180");
                imageFull.addClass("rotate270");
            } else {
                if (imageFull.hasClass("rotate270")) {
                    imageFull.removeClass("rotate270");
                } else {
                    imageFull.addClass("rotate90");
                }
            }
        }
    };
    self.applyRotationLeft = function() {
        var imageFull = $(self.configuration.elements.currentImage);
        if (imageFull.hasClass("rotate180")) {
            imageFull.removeClass("rotate180");
            imageFull.addClass("rotate90");
        } else {
            if (imageFull.hasClass("rotate270")) {
                imageFull.removeClass("rotate270");
                imageFull.addClass("rotate180");
            } else {
                if (imageFull.hasClass("rotate90")) {
                    imageFull.removeClass("rotate90");
                } else {
                    imageFull.addClass("rotate270");
                }
            }
        }
    };
    self.removeRotation = function() {
        var imageFull = $(self.configuration.elements.currentImage);
        imageFull.removeClass("rotate180");
        imageFull.removeClass("rotate90");
        imageFull.removeClass("rotate270");

    }
}
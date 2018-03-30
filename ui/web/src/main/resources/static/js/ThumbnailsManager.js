var ThumbnailsManager = {
    holder: null,
    offset: 0,
    count: 0
};

ThumbnailsManager.init = function (){
    ThumbnailsManager.holder = $("#smallPictureContent");
};
ThumbnailsManager.load = function (offset, count){
    ThumbnailsManager.count = 0;
    ThumbnailsManager.offset = offset;



    ThumbnailsManager.count = count;
};
ThumbnailsManager.clean = function (){
    ThumbnailsManager.holder.find("div.smallPictureBox").remove();
    ThumbnailsManager.offset = 0;
    ThumbnailsManager.count = 0;
};
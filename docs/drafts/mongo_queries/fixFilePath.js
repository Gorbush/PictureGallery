
db.source.find({ filePath : { $regex : "^[^/].*" } }).snapshot().forEach( function (info) {
    info.filePath = '/' + info.filePath; 
    db.source.save(info); 
});
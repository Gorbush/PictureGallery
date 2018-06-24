db.importRequest.find({}).forEach(
    function(e) {
        e.nameL=e.name.toLowerCase();
        e.nameL=e.nameL.replace(/^[_$/\\]*/,"");
        db.importRequest.save(e);
    });
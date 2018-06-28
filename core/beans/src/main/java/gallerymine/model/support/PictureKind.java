package gallerymine.model.support;

import gallerymine.model.ImportSource;
import gallerymine.model.Picture;
import gallerymine.model.Source;

public enum PictureKind {
    GALLERY(Picture.class),
    IMPORT(ImportSource.class),
    SOURCE(Source.class);

    Class entityClass;

    PictureKind(Class clazz) {
        this.entityClass = clazz;
    }

    public Class getEntityClass() {
        return entityClass;
    }
}

package gallerymine.model.support;

import gallerymine.model.ImportSource;
import gallerymine.model.Picture;
import gallerymine.model.PictureInformation;
import gallerymine.model.Source;

public enum PictureGrade {
    GALLERY(Picture.class, "picture"),
    IMPORT(ImportSource.class, "importSource"),
    SOURCE(Source.class, "source");

    Class entityClass;
    String collectionName;

    PictureGrade(Class clazz, String collectionName) {
        this.entityClass = clazz;
        this.collectionName = collectionName;
    }

    public Class getEntityClass() {
        return entityClass;
    }

    public String getCollectionName() {
        return collectionName;
    }
}

package gallerymine.backend.matchers.strategies;

import gallerymine.model.PictureInformation;
import gallerymine.model.support.PictureGrade;

import java.util.Collection;

public interface PictureMatcher {

    int MATCH_LIMIT = 50;


    String getKind();

    default Collection<PictureInformation> findInGallery(PictureInformation source) {
        return find(source, null, PictureGrade.GALLERY);
    }

    default Collection<PictureInformation> findInImport(PictureInformation source, String rootId) {
        return find(source, rootId, PictureGrade.IMPORT);
    }

    Collection<PictureInformation> find(PictureInformation source, String rootId, PictureGrade grade);
}

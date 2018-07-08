package gallerymine.backend.matchers.strategies;

import gallerymine.model.PictureInformation;

import java.util.Collection;

public interface PictureMatcher {

    int MATCH_LIMIT = 50;


    String getKind();

    Collection<PictureInformation> find(PictureInformation source);
}

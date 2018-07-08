package gallerymine.backend.matchers.strategies;

import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.model.PictureInformation;
import gallerymine.model.mvc.SourceCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;

import static gallerymine.model.support.PictureGrade.GALLERY;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class FindSourcesByFileNameAndSize implements PictureMatcher {

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @Override
    public String getKind() {
        return "Name and size";
    }

    @Override
    public Collection<PictureInformation> find(PictureInformation source) {
        SourceCriteria criteria = new SourceCriteria();
        criteria.setFileName(source.getFileName());
        criteria.setFileSize(source.getSize());
        criteria.setSize(MATCH_LIMIT);
        Page<PictureInformation> sources = uniSourceRepository.fetchCustom(criteria, GALLERY.getEntityClass());

        return sources.getContent();
    }
}

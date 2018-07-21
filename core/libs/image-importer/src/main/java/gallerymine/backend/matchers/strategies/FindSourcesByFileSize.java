package gallerymine.backend.matchers.strategies;

import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.model.PictureInformation;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.PictureGrade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class FindSourcesByFileSize implements PictureMatcher {

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @Override
    public String getKind() {
        return "Size";
    }

    @Override
    public Collection<PictureInformation> find(PictureInformation source, String rootId, PictureGrade grade) {
        SourceCriteria criteria = new SourceCriteria();
        criteria.setFileSize(source.getSize());
        criteria.setRequestId(rootId);
        criteria.setSize(MATCH_LIMIT);
        Page<PictureInformation> sources = uniSourceRepository.fetchCustom(criteria, grade.getEntityClass());

        return sources.getContent();
    }
}

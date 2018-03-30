package gallerymine.backend.helpers.matchers.strategies;

import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.model.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class FindSourcesByFileSize {
    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private PictureRepository pictureRepository;

    public Collection<Source> find(Source source) {
        Collection<Source> sources = sourceRepository.findBySize(source.getSize());
        return sources;
    }
}

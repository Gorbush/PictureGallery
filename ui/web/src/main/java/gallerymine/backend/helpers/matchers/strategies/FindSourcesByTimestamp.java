package gallerymine.backend.helpers.matchers.strategies;

import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.model.Source;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class FindSourcesByTimestamp {

    @Autowired
    private SourceRepository sourceRepository;

    public Collection<Source> find(Source sourceToMatch) {
        Collection<Source> sources = sourceRepository.findByTimestamp(sourceToMatch.getTimestamp());
        return sources;
    }
}

package gallerymine.backend.matchers;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.matchers.strategies.*;
import gallerymine.model.Picture;
import gallerymine.model.PictureInformation;
import gallerymine.model.Source;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.SourceMatchReport;
import gallerymine.model.support.SourceRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class SourceFilesMatcher {

    @Autowired
    private FindSourcesByFileNameAndSize findSourcesByFileNameAndSize;

    @Autowired
    private FindSourcesByFileName findSourcesByFileName;

    @Autowired
    private FindSourcesByTimestamp findSourcesByTimestamp;

    @Autowired
    private FindSourcesByFileSize findSourcesByFileSize;

    private List<PictureMatcher> matchers = null;

    private List<PictureMatcher> getMatchers() {
        if (matchers == null) {
            matchers = Arrays.asList(findSourcesByFileNameAndSize,
                    findSourcesByTimestamp,
                    findSourcesByFileName,
                    findSourcesByFileSize
                );
        }
        return matchers;
    }

    public SourceMatchReport matchSourceTo(PictureInformation sourceToMatch) {
        SourceMatchReport report = new SourceMatchReport();

        HashSet<String> sourceIds = new HashSet<>();
        if (sourceToMatch.getId() != null) {
            sourceIds.add(sourceToMatch.getId());
        }

        matchers.forEach(matcher -> {
            matcher.find(sourceToMatch).forEach(
                    source -> {
                        if (!sourceIds.contains(source.getId())) {
                            report.getKind(matcher.getKind()).add(source);
                            sourceIds.add(source.getId());
                        }
                    }
            );
        });

        return report;
    }

    private Collection<SourceRef> convertToRefs(PictureGrade grade, Collection<PictureInformation> sources) {
        Collection<SourceRef> refsSet = new HashSet<>();
        for(PictureInformation source : sources) {
            refsSet.add(new SourceRef(source.getId(), grade));
        }
        return refsSet;
    }

}

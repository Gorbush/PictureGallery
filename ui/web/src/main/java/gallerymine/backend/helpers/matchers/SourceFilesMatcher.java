package gallerymine.backend.helpers.matchers;

import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.helpers.matchers.strategies.FindSourcesByFileName;
import gallerymine.backend.helpers.matchers.strategies.FindSourcesByFileSize;
import gallerymine.backend.helpers.matchers.strategies.FindSourcesByTimestamp;
import gallerymine.model.Picture;
import gallerymine.model.Source;
import gallerymine.model.support.SourceMatchReport;
import gallerymine.model.support.SourceRef;
import gallerymine.model.support.SourceKind;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Component
public class SourceFilesMatcher {

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private PictureRepository pictureRepository;

    @Autowired
    private FindSourcesByFileName findSourcesByFileName;

    @Autowired
    private FindSourcesByTimestamp findSourcesByTimestamp;

    @Autowired
    private FindSourcesByFileSize findSourcesByFileSize;

    public SourceMatchReport matchSourceTo(Source sourceToMatch) {
        SourceMatchReport report = new SourceMatchReport();
        report.setMatchingSource(sourceToMatch);

        HashSet<String> sourceIds = new HashSet<>();
        if (sourceToMatch.getId() != null) {
            sourceIds.add(sourceToMatch.getId());
        }

        Collection<Source> sources = findSourcesByFileName.find(sourceToMatch);
        for(Source source: sources) {
            report.getSources().add(source);
            sourceIds.add(source.getId());
        }
        sources = findSourcesByFileSize.find(sourceToMatch);
        for(Source source: sources) {
            report.getSources().add(source);
            sourceIds.add(source.getId());
        }
        sources = findSourcesByTimestamp.find(sourceToMatch);
        for(Source source: sources) {
            report.getSources().add(source);
            sourceIds.add(source.getId());
        }

        report.setPictures(pictureRepository.findBySourcesIdIn(sourceIds));

//        sourceIds.remove(sourceToMatch.getId());
        report.getSources().remove(sourceToMatch);

        if(report.getPictures().size() == 0) {
            report.setPictures(new HashSet<>());
            Picture picture = makePictureFromSource(sourceToMatch);
            report.getPictures().add(picture);
            picture.getSources().addAll(convertToRefs(SourceKind.UNSET, report.getSources()));
        }

        return report;
    }

    private Collection<SourceRef> convertToRefs(SourceKind unset, Collection<Source> sources) {
        Collection<SourceRef> refsSet = new HashSet<>();
        for(Source source : sources) {
            refsSet.add(new SourceRef(unset, source.getId()));
        }
        return refsSet;
    }

    private Picture makePictureFromSource(Source sourceToMatch) {
        Picture picture = new Picture();
        picture.copyFrom(sourceToMatch);
        return picture;
    }

    public Picture findPictureMatches(Picture picture) {
        return null;
    }
}

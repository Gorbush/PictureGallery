package gallerymine.backend.analyzer;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.MovieBox;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.google.common.collect.Sets;
import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.FileDataSourceImpl;
import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.FileInformation;
import gallerymine.model.PictureInformation;
import gallerymine.model.support.TimestampKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;


/**
 * Analyser for Video files
 * Created by sergii_puliaiev on 6/11/17.
 */
@Component
public class VideoMP4ParserFormatAnalyser extends BaseAnalyser {
    private static Logger log = LoggerFactory.getLogger(VideoMP4ParserFormatAnalyser.class);
    private static Logger logUnknownDirectory = LoggerFactory.getLogger(VideoMP4ParserFormatAnalyser.class);

    public static final String KIND_VIDEO = "Video";

    public static Collection<String> allowedExtensions;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ImportUtils importUtils;

    @Override
    public boolean acceptsFile(String fileName) {
        return super.acceptsFile(fileName);
    }

    @Override
    public boolean acceptsExtension(String fileExt) {
        if (allowedExtensions == null) {
            allowedExtensions = Sets.newHashSet(
                    "mp4",
                    "h264",
                    "mov",
                    "m2v",
                    "m4v",
                    "3gp",
                    "3g2"
            );
        }
        return allowedExtensions.contains(fileExt.toLowerCase());
    }

    @Override
    public boolean gatherFileInformation(Path file, FileInformation info) {
        if (!(info instanceof PictureInformation)) {
            return false;
        }
        PictureInformation source = (PictureInformation)info;

        try {
            FileDataSourceImpl fileSource = new FileDataSourceImpl(file.toFile());
            IsoFile fileInfo = new IsoFile(fileSource);

            if (fileInfo != null) {
                MovieHeaderBox infoBox = fileInfo.getMovieBox().getMovieHeaderBox();
                source.addStamp(TimestampKind.TS_FILE_EXIF_ORIGINAL.create(infoBox.getCreationTime()));
                source.addStamp(TimestampKind.TS_FILE_EXIF_MODIFY.create(infoBox.getModificationTime()));
                long scale = infoBox.getTimescale();
                long durationVal = infoBox.getDuration();
                if (scale != 0) {
                    long duration = durationVal / scale;
                    source.setDurationInSeconds(duration);
                }

                List<MovieBox> movieBoxes = fileInfo.getBoxes(MovieBox.class);
                movieBoxes.forEach(movieBox -> {
                    List<TrackBox> tracks = movieBox.getBoxes(TrackBox.class);
                    tracks.forEach( trackBox -> {
                        TrackHeaderBox trackHeaderBox = trackBox.getTrackHeaderBox();
                        // TODO: Need to find a way to identify is this track audio or video
                        if (trackHeaderBox != null && trackHeaderBox.getHeight() > 0) {
                            source.setHeight((long) trackHeaderBox.getHeight());
                            source.setWidth((long) trackHeaderBox.getWidth());
                            trackHeaderBox.getDuration();
                        }
                        // TODO: Need to add info about languages
                    });
                });
            }

            source.getPopulatedBy().add(KIND_VIDEO);
            return true;
        } catch (Exception e){
            source.setFilled(true);
            source.setExists(true);
            source.setError("Failed to gather info: "+e.getMessage());
            log.error("Failed to gather info for id={} name={} in path {}. Reason: {}", source.getId(), source.getFileName(), source.getFilePath() ,e.getMessage(), e);
            return false;
        }
    }

}

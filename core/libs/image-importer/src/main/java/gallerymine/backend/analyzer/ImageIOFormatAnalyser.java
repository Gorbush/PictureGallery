package gallerymine.backend.analyzer;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.FileInformation;
import gallerymine.model.PictureInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;


/**
 * Analyser for Image files
 * Created by sergii_puliaiev on 6/11/17.
 */
@Component
public class ImageIOFormatAnalyser extends BaseAnalyser {
    private static Logger log = LoggerFactory.getLogger(ImageIOFormatAnalyser.class);
    private static Logger logUnknownDirectory = LoggerFactory.getLogger(ImageIOFormatAnalyser.class);

    public static final String KIND_PICTURE = "Picture";

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
            allowedExtensions = Sets.newHashSet(ImageIO.getReaderFileSuffixes());
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
            BufferedImage imageInfo = importUtils.readImageByImageIO(file, null);

            if (imageInfo != null) {
                //                source.addStamps(imageInfo.get);
                source.setHeight(imageInfo.getHeight());
                source.setWidth(imageInfo.getWidth());
                // imageInfo.getPropertyNames()
            }

            source.getPopulatedBy().add(KIND_PICTURE);
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

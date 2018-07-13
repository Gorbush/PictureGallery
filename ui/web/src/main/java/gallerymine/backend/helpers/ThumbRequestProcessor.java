package gallerymine.backend.helpers;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.services.UniSourceService;
import gallerymine.model.PictureInformation;
import gallerymine.model.Source;
import gallerymine.model.importer.ThumbRequest;
import gallerymine.model.support.PictureGrade;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Creates thumbnail of image
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
@Scope("prototype")
public class ThumbRequestProcessor implements Runnable {

    private static Logger log = LoggerFactory.getLogger(ThumbRequestProcessor.class);

    public static final String KIND_THUMB = "Thumb";

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private ThumbRequest request;

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @Autowired
    private UniSourceService uniSourceService;

    public ThumbRequest getRequest() {
        return request;
    }

    public void setRequest(ThumbRequest request) {
        this.request = request;
    }

    @Override
    public void run() {
        try {
            log.info(" processing started path={}", request.getId(), request.getFilePath());
            processRequest(request);
            log.info(" processing succeed path={}", request.getId(), request.getFilePath());
        } catch (Exception e){
            log.error(" processing failed path={}", request.getId(), request.getFilePath(), e);
        }
    }

    private void processRequest(ThumbRequest requestSrc) {
        log.info(" processing started path={}", requestSrc.getId(), requestSrc.getFilePath());
        if (requestSrc == null) {
            log.info(" processing skipped path={}", requestSrc.getId(), requestSrc.getFilePath());
            return;
        }
        ThumbRequest request = thumbRequestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info(" already processed path={}", requestSrc.getId(), requestSrc.getFilePath());
            return;
        }
        log.info(" started processing id={} path={}", request.getId(), request.getFilePath());
        try {
            File imageFile = new File(request.getFilePath());

            if (!imageFile.exists()) {
                // look for source in Imports
                PictureInformation info = uniSourceRepository.findInfo(request.getSource());
                if (info != null) {
                    imageFile = new File(info.getFullFilePath());
                }
            }

            if (!imageFile.exists()) {
                log.info(" source image not found id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                return;
            }

            File thumbFile = new File(appConfig.getThumbsRootFolder(), request.getThumbName()).getAbsoluteFile();
            if (thumbFile.exists()) {
                log.info(" thumb already exists id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                return;
            }

            BufferedImage img = ImageIO.read(imageFile);

            PictureInformation source = uniSourceRepository.findInfo(request.getSource());
            if (img != null) {
                BufferedImage scaledImage = Scalr.resize(img, 200);
                ImageIO.write(scaledImage, "jpg", thumbFile);
                log.info(" processed and removed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                thumbRequestRepository.delete(request.getId());
                if (source != null) {
                    source.setThumbPath(request.getThumbName());
                }
            } else {
                log.info(" failed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                request.setError("Image cannot be read");
                thumbRequestRepository.save(request);
            }
            if (source != null) {
                uniSourceService.addPopulatedBy(source.getId(), source.getClass(), KIND_THUMB);
            }
        } catch (Exception e) {
            log.error(" processing failed id={} path={}", requestSrc.getId(), requestSrc.getFilePath(), e);
            thumbRequestRepository.save(request);
        }
    }
}

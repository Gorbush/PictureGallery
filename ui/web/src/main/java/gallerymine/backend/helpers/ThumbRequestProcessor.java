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
import java.util.List;

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
                List<PictureInformation> info = uniSourceRepository.findInfo(request.getSource());
                if (info != null && info.size() > 0) {
                    imageFile = new File(info.get(0).getFullFilePath());
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

            if (img != null) {
                BufferedImage scaledImage = Scalr.resize(img, 200);
                ImageIO.write(scaledImage, "jpg", thumbFile);
                log.info(" processed and removed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                thumbRequestRepository.delete(request.getId());
            } else {
                log.info(" failed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.getAbsolutePath());
                request.setError("Image cannot be read");
                thumbRequestRepository.save(request);
            }
            List<PictureInformation> pictures = uniSourceRepository.findInfo(request.getSource());
            if (pictures != null && pictures.size() > 0) {
                log.info("   going to update {} pictures with thumbnail", pictures.size());
                for(PictureInformation pic: pictures) {
                    uniSourceService.retrySave(pic.getId(), pic.getClass(), picture -> {
                        picture.setThumbPath(request.getThumbName());
                        picture.getPopulatedBy().add(KIND_THUMB);
                        return true;
                    });
                }
            } else {
                log.warn("   going to update no pictures with thumbnail, as {} not found ", request.getSource());
            }
        } catch (Exception e) {
            log.error(" processing failed id={} path={}", requestSrc.getId(), requestSrc.getFilePath(), e);
            request.setError(" processing failed. Reason: " + e.getMessage());
            thumbRequestRepository.save(request);
        }
    }
}

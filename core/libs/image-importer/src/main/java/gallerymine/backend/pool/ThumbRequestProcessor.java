package gallerymine.backend.pool;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.services.UniSourceService;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.PictureInformation;
import gallerymine.model.importer.ThumbRequest;
import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
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

    @Autowired
    private ImportUtils importUtils;

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
            log.info("  processing succeed path={}", request.getId(), request.getFilePath());
        } catch (Exception e){
            log.error("  processing failed path={}", request.getId(), request.getFilePath(), e);
        }
    }

    private void processRequest(ThumbRequest requestSrc) {
        if (requestSrc == null) {
            log.info("  processing skipped path={}", requestSrc.getId(), requestSrc.getFilePath());
            return;
        }
        log.info("  processing started path={}", requestSrc.getId(), requestSrc.getFilePath());
        ThumbRequest request = thumbRequestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("  already processed path={}", requestSrc.getId(), requestSrc.getFilePath());
            return;
        }
        log.info("  started processing id={} path={}", request.getId(), request.getFilePath());
        try {
            Path imageFile;

            // look for source in Imports
            List<PictureInformation> info = uniSourceRepository.findInfo(request.getSource());
            if (info != null && info.size() > 0) {
                imageFile = importUtils.calcCompleteFilePath(info.get(0));
            } else {
                log.info("   import image not found id={} path={} source={}", request.getId(), request.getFilePath(), request.getSource());
                return;
            }

            if (!imageFile.toFile().exists()) {
                log.info("   source image not found id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.toFile().getAbsolutePath());
                return;
            }

            File thumbFile = new File(appConfig.getThumbsRootFolder(), request.getThumbName()).getAbsoluteFile();
            if (thumbFile.exists()) {
                log.info("   thumb already exists id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.toFile().getAbsolutePath());
                return;
            }

            importUtils.readImageByImageIO(imageFile, ((reader, imageInfo) -> {
                try {
                    int numThumbs = reader.getNumThumbnails(0);
                    if (numThumbs == 0) {
                        BufferedImage scaledImage = Scalr.resize(imageInfo, 200);
//                      BufferedImageOp resampler = new ResampleOp(200, 200, ResampleOp.FILTER_LANCZOS); // A good default filter, see class documentation for more info
//                      BufferedImage output = resampler.filter(imageInfo, null);
                        ImageIO.write(scaledImage, "jpg", thumbFile);
                    } else {
                        BufferedImage bufferedThumb = reader.readThumbnail(0, 0);
                        ImageIO.write(bufferedThumb, "jpg", thumbFile);
                    }
                    log.info(" processed and removed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.toFile().getAbsolutePath());
                    thumbRequestRepository.delete(request.getId());
                } catch (Exception e) {
                    log.error("Failed to write thumbnail for {}", requestSrc.getFilePath());
                    log.info(" failed id={} path={} image={}", request.getId(), request.getFilePath(), imageFile.toFile().getAbsolutePath());
                    request.setError("Image cannot be read");
                    thumbRequestRepository.save(request);
                }
            }));

            List<PictureInformation> pictures = uniSourceRepository.findInfo(request.getSource());
            if (pictures != null && pictures.size() > 0) {
                log.info("   going to update {} pictures with thumbnail", pictures.size());
                for(PictureInformation picture: pictures) {
                    uniSourceService.retrySave(picture.getId(), picture.getClass(), pic -> {
                        pic.setThumbPath(request.getThumbName());
                        pic.getPopulatedBy().add(KIND_THUMB);
                        return pic;
                    });
                }
            } else {
                log.warn("   going to update no pictures with thumbnail, as {} not found ", request.getSource());
            }
        } catch (Exception e) {
            log.error("  processing failed id={} path={}", requestSrc.getId(), requestSrc.getFilePath(), e);
            request.setError("  processing failed. Reason: " + e.getMessage());
            thumbRequestRepository.save(request);
        }
    }
}

package gallerymine.backend.helpers;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.model.Source;
import gallerymine.model.ThumbRequest;
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

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

    @Autowired
    private SourceRepository sourceRepository;

    private ThumbRequest request;

    public ThumbRequest getRequest() {
        return request;
    }

    public void setRequest(ThumbRequest request) {
        this.request = request;
    }


    @Override
    public void run() {
        try {
            log.info("ThumbRequest processing started path={}/{}", request.getId(), request.getFilePath(), request.getFileName());
            processRequest(request);
            log.info("ThumbRequest processing succeed path={}/{}", request.getId(), request.getFilePath(), request.getFileName());
        } catch (Exception e){
            log.error("ThumbRequest processing failed path={}/{}", request.getId(), request.getFilePath(), request.getFileName(), e);
        }
    }

    private void processRequest(ThumbRequest requestSrc) {
        log.info("ThumbRequest processing started path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName());
        if (requestSrc == null) {
            log.info("ThumbRequest processing skipped path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName());
            return;
        }
        ThumbRequest request = thumbRequestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("ThumbRequest already processed path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName());
            return;
        }
        log.info("ThumbRequest started processing id={} path={}/{}", request.getId(), request.getFilePath(), request.getFileName());
        try {
            File imagePath = new File(appConfig.getSourcesRootFolder(), request.getFilePath());
            File imageFile = new File(imagePath, request.getFileName());
            if (!imageFile.exists()) {
                log.info("ThumbRequest source image not found id={} path={}/{} image={}", request.getId(), request.getFilePath(), request.getFileName(), imageFile.getAbsolutePath());
                return;
            }

            File thumbFile = new File(appConfig.getThumbsRootFolder(), request.getThumbName()).getAbsoluteFile();
            if (thumbFile.exists()) {
                log.info("ThumbRequest thumb already exists id={} path={}/{} image={}", request.getId(), request.getFilePath(), request.getFileName(), imageFile.getAbsolutePath());
                return;
            }

            BufferedImage img = ImageIO.read(imageFile);

            if (img != null) {
                BufferedImage scaledImage = Scalr.resize(img, 200);
                ImageIO.write(scaledImage, "jpg", thumbFile);
                Source source = sourceRepository.findByFilePathAndFileName(request.getFilePath(), request.getFileName());
                if (source != null) {
                    source.setThumbPath(request.getThumbName());
                    sourceRepository.save(source);
                }

                log.info("ThumbRequest processed and removed id={} path={}/{} image={}", request.getId(), request.getFilePath(), request.getFileName(), imageFile.getAbsolutePath());
                thumbRequestRepository.delete(request.getId());
            } else {
                log.info("ThumbRequest failed id={} path={}/{} image={}", request.getId(), request.getFilePath(), request.getFileName(), imageFile.getAbsolutePath());
                request.setError("Image cannot be read");
                thumbRequestRepository.save(request);
            }
        } catch (Exception e) {
            log.error("ThumbRequest processing failed id={} path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName(), e);
            thumbRequestRepository.save(request);
        }
    }
}

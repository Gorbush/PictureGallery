package gallerymine.backend.helpers;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.helpers.analyzer.ImageFormatAnalyser;
import gallerymine.model.Source;
import gallerymine.model.importer.IndexRequest;
import gallerymine.model.importer.ThumbRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import gallerymine.backend.beans.repository.IndexRequestRepository;
import gallerymine.backend.beans.repository.SourceRepository;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.util.Collection;
import java.util.HashSet;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;

/**
 * Processor for images - analysing the content and methadata
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component()
@Scope("prototype")
public class IndexRequestProcessor implements Runnable {

    static final Logger logger = LoggerFactory.getLogger(IndexRequestProcessor.class);
    private static Logger log = LoggerFactory.getLogger(IndexRequestProcessor.class);
    private static Logger logFailed = LoggerFactory.getLogger("failedToIndexError");
    private static Logger logUnknownFormats = LoggerFactory.getLogger("failedToIndexUnknown");
//    private static Logger logFailed = LoggerFactory.getLogger(IndexRequestProcessor.class);
//    private static Logger logUnknownFormats = LoggerFactory.getLogger(IndexRequestProcessor.class);

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private IndexRequestRepository requestRepository;

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

    @Autowired
    private IndexRequestPoolManager pool;

    private ImageFormatAnalyser analyzer = new ImageFormatAnalyser();

    private IndexRequest request;

    public IndexRequest getRequest() {
        return request;
    }

    public void setRequest(IndexRequest request) {
        this.request = request;
    }

    public static Collection<String> allowedExtensions = new HashSet<String>(){{
        add("jpg");
        add("jpeg");
        add("png");
//        add("mp4");
        add("tiff");
        add("psd");
        add("bmp");
        add("gif");
//        add("pdf");
//        add("svg");
    }};

    public void processRequest(IndexRequest requestSrc) {
        log.info("IndexRequest picked up id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
        IndexRequest request = checkRequest(requestSrc);
        if (request == null) {
            log.info("IndexRequest skipped id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
            return;
        }
        log.info("IndexRequest started processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        try {
            Path path = Paths.get(appConfig.getSourcesRootFolder(), request.getPath());

            Path enumeratingDir = null;
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, file -> file.toFile().isDirectory())) {
                int foldersCount = 0;
                for (Path dir : directoryStream) {
                    registerNewFolderRequest(dir.toAbsolutePath().toString(), request);
                    foldersCount++;
                }
                request.setFilesCount(foldersCount);
                requestRepository.save(request);
                log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            } catch (IOException e) {
                request.setFilesCount(-1);
                request.addError("indexing failed for folder "+enumeratingDir);
                requestRepository.save(request);
                log.error("Failed to index. id={} status={} path={} reason='{}'", request.getId(), request.getStatus(), request.getPath(), e.getMessage(), e);
            }

            try {
                request.setStatus(IndexRequest.IndexStatus.FILES_PROCESSING);
                requestRepository.save(request);
                log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

                int filesCount = 0;
                int filesIgnoredCount = 0;
                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, file -> file.toFile().isFile())) {
                    for (Path file : directoryStream) {
                        String fileName = file.toString().toLowerCase();
                        String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1);
                        if (fileName.startsWith(".")) {
                            log.info("Ignore system file {}", file.toAbsolutePath().toString());
                            continue;
                        }
                        if (allowedExtensions.contains(fileExt)) {
                            filesCount++;
                            processPictureFile(file);
                        } else {
                            filesIgnoredCount++;
                            logUnknownFormats.error("Unknown format of file {}", file.toAbsolutePath().toString());
                        }
//                        Thread.sleep(2000);
                    }
                }
                request.setFilesCount(filesCount);
                request.setFilesIgnoredCount(filesIgnoredCount);
                request.setAllFilesProcessed(true);
                request.setStatus(IndexRequest.IndexStatus.ENUMERATED);
                requestRepository.save(request);
            } catch (IOException e) {
                request.setFilesCount(-1);
                request.setFilesIgnoredCount(-1);
                request.setAllFilesProcessed(true);
                request.addError("indexing failed for file "+path);
                requestRepository.save(request);
                log.error(" indexing failed for file {}. Reason: {}", path, e.getMessage());
            }

            checkSubsAndDone(request.getId());

        } catch (Exception e) {
            request.setStatus(IndexRequest.IndexStatus.FAILED);
            request.setError(e.getMessage());
            requestRepository.save(request);
            log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        } finally {
            pool.checkForAwaitingRequests();
        }
    }

    private void checkSubsAndDone(String requestId) {
        if (requestId == null) {
            log.error("Failed to check subs for request id={}", requestId);
            return;
        }
        Page<IndexRequest> subrequests = requestRepository.findByParent(requestId,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));

        boolean allDone = true;
        boolean someErrors = false;

        for (IndexRequest subRequest: subrequests) {
            if (IndexRequest.IndexStatus.FAILED.equals(subRequest.getStatus())) {
                someErrors = true;
                break;
            }
            if (IndexRequest.IndexStatus.DONE.equals(subRequest.getStatus())) {
                continue;
            }
            allDone = false;
        }

        IndexRequest request = requestRepository.findOne(requestId);
        if (request == null) {
            log.error("Failed to check subs for request id={}", requestId);
            return;
        }

        if (allDone) {
            if (someErrors) {
                request.setStatus(IndexRequest.IndexStatus.FAILED);
            } else {
                request.setStatus(IndexRequest.IndexStatus.DONE);
            }
        } else {
            request.setStatus(IndexRequest.IndexStatus.SUB);
        }
        requestRepository.save(request);
        log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        if (allDone && request.getParent() != null) {
            checkSubsAndDone(request.getParent());
        }
    }

    private IndexRequest checkRequest(IndexRequest requestSrc) {
        IndexRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("IndexRequest not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!request.isProcessable()) {
            log.info("IndexRequest status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        request.setStatus(IndexRequest.IndexStatus.ENUMERATING);
        requestRepository.save(request);
        log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        return request;
    }

    public IndexRequest registerNewFolderRequest(String path, IndexRequest parent) throws FileNotFoundException {
        path = appConfig.relativizePathToSource(path);
        IndexRequest newRequest = requestRepository.findByPath(path);
        if (newRequest == null) {
            newRequest = new IndexRequest();
        }
        if (parent != null) {
            newRequest.setParent(parent.getId());
        }
        newRequest.setError(null);
        newRequest.setStatus(IndexRequest.IndexStatus.FOUND);
        newRequest.setPath(path);

        requestRepository.save(newRequest);
        log.info("IndexRequest status changed id={} status={} path={}", newRequest.getId(), newRequest.getStatus(), newRequest.getPath());

        return newRequest;
    }

    private void processPictureFile(Path file) {
        try {
            log.debug("  Visiting file {}", file.toAbsolutePath());

            String fileName = file.getFileName().toString();
            String filePath = file.getParent().toAbsolutePath().toString();
            filePath = appConfig.relativizePath(filePath, appConfig.getSourcesRootFolder());

            Source pictureSource = sourceRepository.findByFilePathAndFileName(filePath, fileName);
            if (pictureSource == null) {
                pictureSource = new Source();
            }
            pictureSource.setFileName(fileName);
            pictureSource.setFilePath(filePath);

            boolean hasThumb = pictureSource.hasThumb();
//            ImageInformation info =
            analyzer.gatherFileInformation(new File(appConfig.getSourcesRootFolder()), pictureSource, !hasThumb);

            if (pictureSource.hasThumb() && !hasThumb) {
                File thumbStoredFile = generatePicThumbName(pictureSource);
                File thumbGeneratedFile = new File(pictureSource.getThumbPath());
                if (thumbGeneratedFile.exists()) {
                    String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.getAbsolutePath());
                    thumbGeneratedFile.renameTo(thumbStoredFile);
                    pictureSource.setThumbPath(relativeStoredPath);
                }
            }
            sourceRepository.save(pictureSource);

            if (!pictureSource.hasThumb()) {
                log.warn(" No thumbnail injected - need to generate one for {} in {}", pictureSource.getFileName(), pictureSource.getFilePath());
                File thumbStoredFile = generatePicThumbName(pictureSource);
                String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.getAbsolutePath());
                ThumbRequest request = new ThumbRequest(pictureSource.getFilePath(), pictureSource.getFileName(), relativeStoredPath);
                thumbRequestRepository.save(request);
            }

        } catch (Exception e) {
            log.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
            logFailed.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
        }
    }

    private File generatePicThumbName(Source pic) {
        DateTime ts = pic.getTimestamp();
        if (ts == null) {
            ts = new DateTime();
        }
        long millis = ts.getMillis();
        String picFileName = millis+"_"+pic.getFileName()+".jpg";
        String picFolderName =
                "/"+ts.getYear()+
                        "/"+ts.getMonthOfYear()+
                        "/"+ts.getDayOfMonth()+
                        "/"+ Long.toString(millis % 100000);
        File thumbFolder = new File(appConfig.getThumbsRootFolder(), picFolderName);
        thumbFolder.mkdirs();
        return new File(thumbFolder, picFileName);
    }

    @Override
    public void run() {
        try {
            log.info("IndexRequest processing started for {}", request.getPath());
            processRequest(request);
            log.info("IndexRequest processing succeed for {}", request.getPath());
        } catch (Exception e){
            log.error("IndexRequest processing failed for {} Reason: {}", request.getPath(), e.getMessage(), e);
        }
    }
}

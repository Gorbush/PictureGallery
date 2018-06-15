package gallerymine.backend.importer;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.helpers.analyzer.ImageFormatAnalyser;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.ImportSource;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.importer.ThumbRequest;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

@Component
@Scope("prototype")
public class ImportProcessor implements Runnable {

    private static Logger log = LoggerFactory.getLogger(ImportProcessor.class);
    private static Logger logFailed = LoggerFactory.getLogger("failedToIndexError");
    private static Logger logUnknownFormats = LoggerFactory.getLogger("failedToIndexUnknown");

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

    @Autowired
    protected AppConfig appConfig;

    @Autowired
    protected ImportUtils importUtils;

    @Autowired
    protected ImportRequestRepository requestRepository;

    @Autowired
    protected ProcessRepository processRepository;

    @Autowired
    protected ImportSourceRepository sourceRepository;

    @Autowired
    private ImageFormatAnalyser analyzer = new ImageFormatAnalyser();

    private ImportRequest request;
    private ImportRequestPoolManager pool;

    public ImportRequest registerImport(Path originalPath, boolean enforceImport) {
        ImportRequest request = new ImportRequest(originalPath);
        log.info("  import folder %s", originalPath.toFile().getAbsolutePath());

        if (!originalPath.toFile().exists()) {
            log.info("Original path not found %s", originalPath.toFile().getAbsolutePath());
            return request.addError("Original path not found").markFailed();
        }

        if (!originalPath.toFile().isDirectory()) {
            log.info("Original path is not a folder %s", originalPath.toFile().getAbsolutePath());
            return request.addError("Original path is not a folder").markFailed();
        }

        DateTime importStamp = new DateTime();

        Path importFolder = importUtils.makeNewImportStamp(importStamp);
        request.setPath(importFolder.toFile().getAbsolutePath());
        log.info("  import folder %s", importFolder.toFile().getAbsolutePath());

        boolean result = importFolder.toFile().mkdirs();
        if (!result) {
            log.info("Failed to create import folder %s", importFolder.toFile().getAbsolutePath());
            return request.addError("Failed to create import folder").markFailed();
        }

        Path importMarkFile = importUtils.getImportMarkFile(originalPath);
        if (importMarkFile.toFile().exists()) {
            String content = importUtils.getImportMarkFileContent(originalPath);
            log.info("Folder was processed before on "+content);
            request.addError("Folder was processed before on "+content);
            if (!enforceImport) {
                return request.markFailed();
            }
        }

        importUtils.createImportMarkFile(originalPath, importStamp);
        importUtils.createImportMarkFile(importFolder, importStamp);

        request.setCreated(importStamp);

        requestRepository.save(request);
        request.setRootId(request.getId());
        requestRepository.save(request);

        log.info("Saved request to import folder %s", originalPath.toFile().getAbsolutePath());
        return request;
    }

    public void run() {
        Process process = null;
        try {
            log.info("ImportRequest processing started for {}", request.getPath());
            if (StringUtils.isNotBlank(request.getIndexProcessId())) {
                process = processRepository.findOne(request.getIndexProcessId());
            }
            if (process == null) {
                process = new Process();
                process.setName("Pictures Folder Import ?");
                process.setType(ProcessType.IMPORT);
            }
            process.setStatus(ProcessStatus.STARTED);
            process.setStarted(DateTime.now());
            processRepository.save(process);

            processRequest(request, process);


            log.info("ImportRequest processing started successfuly for {}", request.getPath());
        } catch (Exception e){
            log.error("ImportRequest processing failed for {} Reason: {}", request.getPath(), e.getMessage(), e);
        }
}

    private ImportRequest checkRequest(ImportRequest requestSrc) {
        ImportRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("ImportRequest not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!request.isProcessable()) {
            log.info("ImportRequest status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        request.setStatus(ENUMERATING);
        requestRepository.save(request);
        log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        return request;
    }

    public void processRequest(ImportRequest requestSrc, Process process) {
        log.info("ImportRequest picked up id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
        ImportRequest request = checkRequest(requestSrc);
        if (request == null) {
            log.info("ImportRequest skipped id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
            return;
        }
        log.info("ImportRequest started processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        try {
            Path path = Paths.get(appConfig.getSourcesRootFolder(), request.getPath());

            Path enumeratingDir = null;
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, file -> file.toFile().isDirectory())) {
                int foldersCount = 0;
                for (Path dir : directoryStream) {
                    registerNewFolderRequest(dir.toAbsolutePath().toString(), request, process.getId());
                    foldersCount++;
                }
                request.setFoldersCount(foldersCount);
                requestRepository.save(request);
                log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            } catch (IOException e) {
                request.setFoldersCount(-1);
                request.addError("ImportRequest indexing failed for folder "+enumeratingDir);
                requestRepository.save(request);
                log.error("Failed to index. id={} status={} path={} reason='{}'", request.getId(), request.getStatus(), request.getPath(), e.getMessage(), e);
            }

            try {
                request.setStatus(FILES_PROCESSING);
                requestRepository.save(request);
                log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

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
                        if (analyzer.acceptsExtension(fileExt)) {
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
                request.setStatus(ENUMERATED);
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
            request.setStatus(FAILED);
            request.setError(e.getMessage());
            requestRepository.save(request);
            log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        } finally {
            pool.checkForAwaitingRequests();
        }
    }

    private void checkSubsAndDone(String requestId) {
        if (requestId == null) {
            log.error("Failed to check subs for request id={}", requestId);
            return;
        }
        Page<ImportRequest> subrequests = requestRepository.findByParent(requestId,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));

        boolean allDone = true;
        boolean someErrors = false;

        for (ImportRequest subRequest: subrequests) {
            if (FAILED.equals(subRequest.getStatus())) {
                someErrors = true;
                break;
            }
            if (DONE.equals(subRequest.getStatus())) {
                continue;
            }
            allDone = false;
        }

        ImportRequest request = requestRepository.findOne(requestId);
        if (request == null) {
            log.error("Request not found, failed to check subs for id={}", requestId);
            return;
        }

        if (allDone) {
            if (someErrors) {
                request.setStatus(FAILED);
            } else {
                request.setStatus(DONE);
            }
        } else {
            request.setStatus(SUB);
        }
        requestRepository.save(request);

        log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        if (request.getStatus().isFinal()) {
            log.info("ImportRequest finished id={} status={}", requestId, request.getStatus());
        }

        if (request.getStatus().isFinal() && StringUtils.isNotBlank(request.getIndexProcessId()) && StringUtils.isBlank(request.getParent())) {
            Process process = processRepository.findOne(request.getIndexProcessId());
            if (process != null) {
                if (request.getStatus().equals(ImportRequest.ImportStatus.DONE)) {
                    process.setStatus(ProcessStatus.FINISHED);
                } else {
                    process.setStatus(ProcessStatus.FAILED);
                }
                process.setFinished(DateTime.now());
                processRepository.save(process);
                log.info("Process finished id={} status={}", process.getId(), process.getStatus());
            } else {
                log.info("Process not found id={} importRequest={}", process.getId(), request.getId());
            }
        }

        if (allDone && request.getParent() != null) {
            checkSubsAndDone(request.getParent());
        }
    }

    private void processPictureFile(Path file) {
        try {
            log.debug("  Visiting file {}", file.toAbsolutePath());

            String fileName = file.getFileName().toString();
            String filePath = file.getParent().toAbsolutePath().toString();
            filePath = appConfig.relativizePath(filePath, appConfig.getSourcesRootFolder());

            ImportSource pictureSource = new ImportSource();
            pictureSource.setFileName(fileName);
            pictureSource.setFilePath(filePath);

            boolean hasThumb = pictureSource.hasThumb();
            analyzer.gatherFileInformation(new File(appConfig.getSourcesRootFolder()), pictureSource, !hasThumb);

            if (pictureSource.hasThumb() && !hasThumb) {
                Path thumbStoredFile = importUtils.generatePicThumbName(pictureSource.getFileName(), pictureSource.getTimestamp());
                File thumbGeneratedFile = new File(pictureSource.getThumbPath());
                if (thumbGeneratedFile.exists()) {
                    String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.toFile().getAbsolutePath());
                    thumbGeneratedFile.renameTo(thumbStoredFile.toFile());
                    pictureSource.setThumbPath(relativeStoredPath);
                }
            }
            sourceRepository.save(pictureSource);

            if (!pictureSource.hasThumb()) {
                log.warn(" No thumbnail injected - need to generate one for {} in {}", pictureSource.getFileName(), pictureSource.getFilePath());
                Path thumbStoredFile = importUtils.generatePicThumbName(pictureSource.getFileName(), pictureSource.getTimestamp());
                String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.toFile().getAbsolutePath());
                ThumbRequest request = new ThumbRequest(pictureSource.getFilePath(), pictureSource.getFileName(), relativeStoredPath);
                thumbRequestRepository.save(request);
            }

        } catch (Exception e) {
            log.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
            logFailed.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
        }
    }

    public ImportRequest registerNewFolderRequest(String path, ImportRequest parent, String indexProcessId) throws FileNotFoundException {
        path = appConfig.relativizePathToSource(path);
        ImportRequest newRequest = requestRepository.findByPath(path);
        if (newRequest == null) {
            newRequest = new ImportRequest();
        }
        if (parent != null) {
            newRequest.setParent(parent.getId());
            newRequest.setRootId(parent.getRootId());
        }
        newRequest.setIndexProcessId(indexProcessId);
        newRequest.setError(null);
        newRequest.setStatus(START);
        newRequest.setPath(path);

        requestRepository.save(newRequest);
        log.info("ImportRequest status changed id={} status={} path={}", newRequest.getId(), newRequest.getStatus(), newRequest.getPath());

        return newRequest;
    }

    public void setRequest(ImportRequest request) {
        this.request = request;
    }

    public void setPool(ImportRequestPoolManager pool) {
        this.pool = pool;
    }
}
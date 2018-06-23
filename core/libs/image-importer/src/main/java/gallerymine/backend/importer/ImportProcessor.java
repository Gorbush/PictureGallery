package gallerymine.backend.importer;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.helpers.analyzer.ImageFormatAnalyser;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.services.ImportService;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.FileInformation;
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
import java.util.Collection;

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
    private ImageFormatAnalyser imageAnalyzer;

    @Autowired
    private GenericFileAnalyser fileAnalyzer;

    @Autowired
    private ImportSourceRepository importSourceRepository;

    @Autowired
    private ImportService importService;

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
            Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

            if (!path.toFile().exists()) {
                String error = String.format("Path not found for request : %s", path.toFile().getAbsolutePath());
                log.error("ImportRequest "+error);
                request.addError(error);
                request.setStatus(FAILED);
                requestRepository.save(request);
                process.addError(error);
                processRepository.save(process);

                checkSubsAndDone(request.getId(), null);
                return;
            }
            Path enumeratingDir = null;
            try (DirectoryStream<Path> directoryStreamOfFolders = Files.newDirectoryStream(path, file -> file.toFile().isDirectory())) {
                int foldersCount = 0;
                for (Path dir : directoryStreamOfFolders) {
                    enumeratingDir = dir;
                    String relative = appConfig.relativizePathToImport(dir);
                    importService.registerNewImportFolderRequest(relative, request, process.getId());
                    foldersCount++;
                }
                request.setFoldersCount(foldersCount);
                request.getStats().incFolders();
                requestRepository.save(request);
                log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            } catch (IOException e) {
                request.setFoldersCount(-1);
                request.addError("ImportRequest indexing failed for folder "+enumeratingDir);
                requestRepository.save(request);
                log.error("ImportRequest Failed to index. id={} status={} path={} reason='{}'", request.getId(), request.getStatus(), request.getPath(), e.getMessage(), e);
            }

            try {
                request.setStatus(FILES_PROCESSING);
                requestRepository.save(request);
                log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
                Path importRootFolder = Paths.get(request.getRootPath());
                int filesCount = 0;
                int filesIgnoredCount = 0;
                try (DirectoryStream<Path> directoryStreamOfFiles = Files.newDirectoryStream(path, file -> file.toFile().isFile())) {
                    for (Path file : directoryStreamOfFiles) {
                        String fileName = file.toString().toLowerCase();
                        String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1);
                        if (fileName.startsWith(".")) {
                            log.info("ImportRequest Ignore system file {}", file.toAbsolutePath().toString());
                            continue;
                        }

                        ImportSource info = new ImportSource();
                        info.setImportRequestId(request.getId());
                        info.setIndexProcessId(request.getIndexProcessId());
                        info.setImportRequestRootId(request.getRootId());
                        fileAnalyzer.gatherFileInformation(file, importRootFolder, info);

                        if (imageAnalyzer.acceptsExtension(fileExt)) {
                            filesCount++;
                            request.getStats().incFiles();
                            processPictureFile(file, importRootFolder, info);

                            if (!info.isFailed()) {
                                if (!checkIfDuplicate(file, request, info)) {
                                    Path targetFolder = importUtils.moveToApprove(file, request.getRootPath());
                                    request.getStats().incMovedToApprove();
                                    info.setRootPath(targetFolder.toFile().getAbsolutePath());
                                }
                            } else {
                                filesIgnoredCount++;
                                request.getStats().incFailed();
                                Path targetFolder = importUtils.moveToFailed(file, request.getRootPath());
                                info.setRootPath(targetFolder.toFile().getAbsolutePath());
                                logUnknownFormats.error("ImportRequest Unknown format of file {}", file.toAbsolutePath().toString());
                            }
                        } else {
                            Path targetFolder = importUtils.moveToFailed(file, request.getRootPath());
                            info.setRootPath(targetFolder.toFile().getAbsolutePath());
                            request.getStats().incFailed();
                        }
                        importSourceRepository.save(info);
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
                log.error("ImportRequest  indexing failed for file {}. Reason: {}", path, e.getMessage());
            }

            checkSubsAndDone(request.getId(), null);

        } catch (Exception e) {
            request.setStatus(FAILED);
            request.setError(e.getMessage());
            requestRepository.save(request);
            importService.finishRequestProcessing(request);
            log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        } finally {
            pool.checkForAwaitingRequests();
        }
    }

    /**
     * Not only checks if we already have such kind of file imported, but also identifies
     * match - similar or duplicate and moves the file to the right import subfolder
     * */
    private boolean checkIfDuplicate(Path file, ImportRequest request, FileInformation info) throws IOException {
        // TODO make better check for duplicates, disable for now as it is equal to similar
//        boolean duplicatesFound = importUtils.findDuplicates(file.toFile().getName(), file.toFile().length());
//        if (duplicatesFound) {
//            importUtils.moveToDuplicates(file, request.getRootPath());
//            return true;
//        }
        boolean similarFound = importUtils.findSimilar(file.toFile().getName(), file.toFile().length());
        if (similarFound) {
            Path targetFolder = importUtils.moveToSimilar(file, request.getRootPath());
            info.setRootPath(targetFolder.toFile().getAbsolutePath());
            return true;
        }
        return false;
    }

    private void checkSubsAndDone(String requestId, ImportRequest child) {
        if (requestId == null) {
            log.error("ImportRequest Failed to check subs for request id={}", requestId);
            return;
        }
        Collection<ImportRequest> subrequests = requestRepository.findByParent(requestId);

        boolean allDone = true;
        boolean someErrors = false;

        for (ImportRequest subRequest: subrequests) {
            if (FAILED.equals(subRequest.getStatus())) {
                someErrors = true;
            }
            if (!subRequest.getStatus().isFinal()) {
                allDone = false;
            }
            if (someErrors && !allDone) {
                // we already noticed that we have some unfinished work and some failed tasks
                // no need to check everything
                break;
            }
        }

        ImportRequest request = requestRepository.findOne(requestId);
        if (request == null) {
            log.error("ImportRequest Request not found, failed to check subs for id={}", requestId);
            return;
        }

        if (child != null) {
            log.info("ImportRequest Adding child substats from id={}", child.getId());
            request.getSubStats().append(child.getTotalStats());
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
        if (request.getStatus().isFinal() &&
                StringUtils.isNotBlank(request.getIndexProcessId()) && // has process
                StringUtils.isBlank(request.getParent())) {            // this is the top import request
            Process process = processRepository.findOne(request.getIndexProcessId());
            if (process != null) {
                log.info("ImportRequest updating process of id={} process={}", requestId, request.getIndexProcessId());
                if (request.getStatus().equals(ImportRequest.ImportStatus.DONE)) {
                    process.addNote("Import finished");
                    process.setStatus(ProcessStatus.FINISHED);
                } else {
                    process.addError("Import failed");
                    process.setStatus(ProcessStatus.FAILED);
                }
                processRepository.save(process);
                log.info("ImportRequest Process finished id={} status={}", process.getId(), process.getStatus());
            } else {
                log.info("ImportRequest Process not found id={} importRequest={}", request.getIndexProcessId(), request.getId());
            }
        }

        if (allDone && StringUtils.isNotBlank(request.getParent())) {
            log.info("ImportRequest processing parent of id={} parent={}", requestId, request.getParent());
            checkSubsAndDone(request.getParent(), request);
        }

        if (request.getStatus().isFinal()) {
            importService.finishRequestProcessing(request);
            log.info("ImportRequest finished id={} status={}", requestId, request.getStatus());
        }
    }

    private void processPictureFile(Path file, Path importRootFolder, ImportSource info) {
        try {
            log.debug("  Visiting file {}", file.toAbsolutePath());

            boolean hadThumb = info.hasThumb();

            imageAnalyzer.gatherFileInformation(file, info, !hadThumb);

            if (info.hasThumb() && !hadThumb) {
                Path thumbStoredFile = importUtils.generatePicThumbName(info.getFileName(), info.getTimestamp());
                File thumbGeneratedFile = new File(info.getThumbPath());
                if (thumbGeneratedFile.exists()) {
                    String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.toFile().getAbsolutePath());
                    if (!thumbGeneratedFile.renameTo(thumbStoredFile.toFile())) {
                        log.warn("Thumb file renaming for %s is failed", file.toFile().getAbsolutePath());
                    };
                    info.setThumbPath(relativeStoredPath);
                }
            }

            if (!info.hasThumb()) {
                log.warn(" No thumbnail injected - need to generate one for {} in {}", info.getFileName(), info.getFilePath());
                Path thumbStoredFile = importUtils.generatePicThumbName(info.getFileName(), info.getTimestamp());
                String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.toFile().getAbsolutePath());
                ThumbRequest request = new ThumbRequest(info.getFilePath(), info.getFileName(), relativeStoredPath);
                thumbRequestRepository.save(request);
            }
        } catch (Exception e) {
            info.addError("Failed: "+e.getMessage());
            log.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
            logFailed.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
        }
    }

    public ImportRequest registerNewImportFolderRequest(String path, ImportRequest parent, String indexProcessId) throws ImportFailedException {
        try {
            path = appConfig.relativizePathToImport(path);
        } catch (FileNotFoundException e) {
            throw new ImportFailedException("File not found "+ path, e);
        }
        ImportRequest newRequest = requestRepository.findByPath(path);
        if (newRequest == null) {
            newRequest = new ImportRequest();
        }
        if (parent != null) {
            newRequest.setParent(parent.getId());
            newRequest.setRootId(parent.getRootId());
            newRequest.setRootPath(parent.getRootPath());
        } else {
            newRequest.setRootPath(path);
        }
        newRequest.setIndexProcessId(indexProcessId);
        newRequest.setError(null);
        newRequest.setStatus(INIT);
        newRequest.setPath(path);

        requestRepository.save(newRequest);
        newRequest.setStatus(START);
        if (parent == null) {
            newRequest.setRootId(newRequest.getId());
        }
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
package gallerymine.backend.importer;

import gallerymine.backend.analyzer.*;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.data.RetryVersion;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.pool.ThumbRequestPool;
import gallerymine.model.*;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.importer.ThumbRequest;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

@Component
@Scope("prototype")
public class ImportProcessor extends ImportProcessorBase {

    private static Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    public static final ImportPoolManagerBase.StatusHolder STATUSES =
            ImportApproveRequestPoolManager.StatusHolder.define(ENUMERATING_AWAIT, ENUMERATING, ENUMERATED, ENUMERATION_COMPLETE)
                    .processing(TO_ENUMERATE)
                    .abandoned(ENUMERATING_AWAIT, ENUMERATING, ENUMERATED);

    public static final String KIND_THUMB = "Thumb";
    public static final String KIND_PICTURE = "Picture";

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

    @Autowired
    private ThumbRequestPool thumbRequestPool;

    @Autowired
    private IgnorableFileAnayser ignorableFileAnayser;

    @Autowired
    protected GenericFileAnalyser fileAnalyzer;

    @Autowired
    protected ImageDrewFormatAnalyser imageDrewAnalyzer;

    @Autowired
    private ImageIOFormatAnalyser imageIOFormatAnalyser;

    @Autowired
    private VideoMP4ParserFormatAnalyser videoMP4ParserFormatAnalyser;

    private List<BaseAnalyser> analysers;

    public ImportProcessor() {
        super(STATUSES, ProcessType.IMPORT);
    }

    public List<BaseAnalyser> getAnalysers() {
        if (analysers == null) {
            synchronized (this) {
                if (analysers == null) {
                    analysers = Arrays.asList(
                            fileAnalyzer,

                            // Image parsers
                            imageDrewAnalyzer,
                            imageIOFormatAnalyser,

                            // Video parsers
                            videoMP4ParserFormatAnalyser);
                }
            }
        }
        return analysers;
    }

    /*
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
        request.setName(importFolder.toFile().getName());
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
    } */

    public void requestProcessing() throws ImportFailedException {
        log.info("   import processing start path={}", request.getPath());
        Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

        if (!validateImportRequest(process, path))
            return;


        request = requestService.retrySave(request.getId(), requestEntity -> {
                    requestEntity.setStatus(statusHolder.getInProcessing());
                    requestEntity.getStats(processType).getFiles().set(-1);
                    return requestEntity;
                });
        updateMarker();

        Path enumeratingDir = null;
        log.info("  folders enumerating path={}", request.getPath());
        try (DirectoryStream<Path> directoryStreamOfFolders = Files.newDirectoryStream(path, file -> file.toFile().isDirectory())) {
            int foldersCount = 0;
            for (Path dir : directoryStreamOfFolders) {
                enumeratingDir = dir;
                String relative = appConfig.relativizePathToImport(dir);
                importService.registerNewImportFolderRequest(relative, request, process.getId());
                foldersCount++;
            }

            final int doneFoldersCount = foldersCount;
            request = requestService.retrySave(request.getId(), requestEntity -> {
                        requestEntity.setFoldersCount(doneFoldersCount);
                        requestEntity.getStats(processType).getFolders().set(doneFoldersCount);
                        return requestEntity;
                    });
            log.info("    folders processed {}. path={}", foldersCount, request.getPath());
        } catch (IOException e) {
            requestService.addError(request.getId(), "ImportRequest indexing failed for folder "+enumeratingDir);
            log.error(" Failed to index. path={} reason='{}'", request.getPath(), e.getMessage(), e);
        }

        log.info("    files enumerating path={}", request.getPath());
        try {
            Path importRootFullFolder = Paths.get(appConfig.getImportRootFolder(), request.getRootPath());
            int filesCount = 0;
            int filesCountIgnored = 0;
            int filesCountFailed = 0;
            int filesCountSucceed = 0;
            try (DirectoryStream<Path> directoryStreamOfFiles = Files.newDirectoryStream(path, file -> file.toFile().isFile())) {
                for (Path file : directoryStreamOfFiles) {
                    String fileName = file.toString().toLowerCase();
                    if (fileName.startsWith(".")) {
                        log.info(" Ignore system file {}", file.toAbsolutePath().toString());
                        continue;
                    }

                    ImportSource info = new ImportSource();
                    info.setImportRequestId(request.getId());
                    info.addIndexProcessId(process.getId());
                    info.setImportRequestRootId(request.getRootId());

                    info.setRootPath(request.getRootPath());
                    info.setFilePath(appConfig.relativizePath(file.getParent(), importRootFullFolder));
                    info.setFileName(file.toFile().getName());
                    info.setFileNameOriginal(file.toFile().getName());

                    filesCount++;

                    if (ignorableFileAnayser.accepts(file.toFile().getName())) {
                        log.debug("    file ignored {}", info.getFileWithPath());
                        Path targetFolder = importUtils.moveToFailed(info);
                        info.setRootPath(targetFolder.toString());
                        filesCountIgnored++;
                        info.setStatus(InfoStatus.SKIPPED);
                    } else {
                        log.debug("    file parsing {}", info.getFileWithPath());
                        List<BaseAnalyser> analysers = getAnalysers();

                        for (BaseAnalyser analyser : analysers) {
                            if (analyser.acceptsFile(info.getFileName())) {
                                processPictureFile(file, info, analyser);
                            }
                        }
                        if (info.getPopulatedBy().contains(KIND_PICTURE)) {
                            if (!info.isFailed()) {
                                Path targetRootPath = importUtils.moveToApprove(info);
                                info.setRootPath(targetRootPath.toString());

                                info.setStatus(InfoStatus.ANALYSING);
                                filesCountSucceed++;
                            } else {
                                filesCountFailed++;
                                Path targetFolder = importUtils.moveToFailed(info);
                                info.setRootPath(targetFolder.toString());
                                info.setStatus(InfoStatus.FAILED);
                                info.addError(" Wrong format of file");
                                logUnknownFormats.error(" Wrong format of file {}", file.toAbsolutePath().toString());
                            }
                        } else {
                            Path targetFolder = importUtils.moveToFailed(info);
                            info.setRootPath(targetFolder.toString());
                            filesCountFailed++;
                            info.setStatus(InfoStatus.FAILED);
                            info.addError(" Unknown format of file");
                            logUnknownFormats.error(" Unknown format of file {}", file.toAbsolutePath().toString());
                        }
                    }
                    uniSourceRepository.saveByGrade(info);
                    if (!info.hasThumb() && !
                            (
                                    InfoStatus.FAILED.equals(info.getStatus())
                            ||
                                    InfoStatus.SKIPPED.equals(info.getStatus())
                            )
                       ) {
                        log.info("     No thumbnail injected - need to generate one for {} in {}", info.getFileName(), info.getFilePath());
                        Path thumbStoredFile = importUtils.generatePicThumbName(info.getFileName(), info.getTimestamp());
                        ThumbRequest request = new ThumbRequest(info.getFullFilePath(), thumbStoredFile.toString());
                        request.setSource(info.getId());
                        thumbRequestRepository.save(request);
                        thumbRequestPool.executeRequest(request);
                    }
                }
            }

            final int doneFilesCount = filesCount;
            final int doneFilesCountIgnored = filesCountIgnored;
            final int doneFilesCountFailed = filesCountFailed;
            final int doneFilesCountSucceed = filesCountSucceed;
            request = requestService.retrySave(request.getId(), requestEntity -> {
                        requestEntity.setFilesCount(doneFilesCount);
                        requestEntity.setFilesIgnoredCount(doneFilesCountIgnored+doneFilesCountFailed);
                        ImportRequest.ImportStats stats = requestEntity.getStats(processType);
                        stats.setAllFilesProcessed(true);
                        stats.getMovedToApprove().set(doneFilesCountSucceed);
                        stats.getFiles().set(doneFilesCount);
                        stats.getFilesDone().set(doneFilesCountSucceed);
                        stats.getSkipped().set(doneFilesCountIgnored);
                        stats.getFailed().set(doneFilesCountFailed);
                        return requestEntity;
                    });
            log.info("     files processing done {} or {} succeeded (ignored:{}  failed:{}). path={}",
                    filesCount, filesCountSucceed, filesCountIgnored, filesCountFailed, request.getPath());
        } catch (IOException e) {
            request = requestService.retrySave(request.getId(), requestEntity -> {
                    requestEntity.setFilesCount(-1);
                    requestEntity.setFilesIgnoredCount(-1);
                    requestEntity.getStats(processType).setAllFilesProcessed(true);
                    requestEntity.addError("indexing failed for file " + path);
                    return requestEntity;
                    });
            log.error("     import processing failed for file {}. Reason: {}", path, e.getMessage());
        }
        log.info("   import processing done path={}", request.getPath());
    }

    private void processPictureFile(Path file, ImportSource info, BaseAnalyser analyser) {
        try {
            log.debug("  Visiting file {}", file.toAbsolutePath());

            analyser.gatherFileInformation(file, info);

            // If Thumb was generated during Image Information extraction - move it to the right location
            if (info.hasThumb() && !info.getThumbPath().startsWith(appConfig.getThumbsRootFolder())) {
                Path thumbStoredFile = importUtils.generatePicThumbName(info.getFileName(), info.getTimestamp());
                Path thumbGeneratedFile = Paths.get(appConfig.getThumbsRootFolder()).resolve(info.getThumbPath());
                if (thumbGeneratedFile.toFile().exists()) {
                    Path thumbStoredPath = Paths.get(appConfig.getThumbsRootFolder()).resolve(thumbStoredFile);
                    if (!thumbGeneratedFile.toFile().renameTo(thumbStoredPath.toFile())) {
                        log.warn("    Thumb file renaming for %s is failed", info.getFileWithPath());
                    }
                    log.info("    Thumb was moved to the right location for path={}", info.getFileWithPath());
                    info.setThumbPath(thumbStoredFile.toString());
                } else {
                    log.warn("    Thumb path present, but file not found for path={}", info.getFileWithPath());
                    info.setThumbPath(null);
                }
            }
        } catch (Exception e) {
            info.addError("Failed: "+e.getMessage());
            log.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
            logFailed.error("   Failed to process {}. Reason: {}", file.toAbsolutePath(), e.getMessage(), e);
        }
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest registerNewImportFolderRequest(String path, ImportRequest parent, String indexProcessId) throws ImportFailedException {
        try {
            path = appConfig.relativizePathToImport(path);
        } catch (FileNotFoundException e) {
            throw new ImportFailedException("File not found "+ path, e);
        }
        log.info(" register new Import path={} processId={} {}", path, indexProcessId, parent != null ? ("path="+parent.getPath()) : "");
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
        newRequest.setStatus(INIT);
        newRequest.setPath(path);

        newRequest.addIndexProcessId(indexProcessId);
        newRequest.setActiveProcessType(ProcessType.IMPORT);
        newRequest.setActiveProcessId(indexProcessId);
        newRequest.setName(Paths.get(path).toFile().getName());

        requestRepository.save(newRequest);

        newRequest.setStatus(TO_ENUMERATE);
        if (parent == null) {
            newRequest.setRootId(newRequest.getId());
        }
        requestRepository.save(newRequest);
        log.info("    registered as id={} status={} path={} processid={} {}", newRequest.getId(), newRequest.getStatus(), newRequest.getPath(),
                indexProcessId, parent != null ? ("path="+parent.getPath()) : "");

        return newRequest;
    }

}
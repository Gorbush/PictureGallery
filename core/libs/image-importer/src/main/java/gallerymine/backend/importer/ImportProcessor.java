package gallerymine.backend.importer;

import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.helpers.analyzer.ImageFormatAnalyser;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.ImportService;
import gallerymine.model.FileInformation;
import gallerymine.model.ImportSource;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.importer.ThumbRequest;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicLong;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

@Component
@Scope("prototype")
public class ImportProcessor extends ImportProcessorBase {

    private static Logger log = LoggerFactory.getLogger(ImportProcessor.class);

    public static final ImportPoolManagerBase.StatusHolder STATUSES =
            ImportApproveRequestPoolManager.StatusHolder.define(ENUMERATING_AWAIT, ENUMERATING, ENUMERATED, ENUMERATION_COMPLETE)
                    .processing(TO_ENUMERATE)
                    .abandoned(ENUMERATING_AWAIT, ENUMERATING, ENUMERATED);

    @Autowired
    private ThumbRequestRepository thumbRequestRepository;

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

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    public ImportProcessor() {
        super(STATUSES, ProcessType.IMPORT);
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

    public void requestProcessing(ImportRequest requestSrc, Process process) throws ImportFailedException {
        log.warn("   import processing start id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

        if (!validateImportRequest(process, path))
            return;

        ImportRequest.ImportStatus oldStatus = request.getStatus();
        request.setStatus(statusHolder.getInProcessing());
        request.getStats(processType).getFiles().set(-1);
        requestRepository.save(request);

        Path enumeratingDir = null;
        log.info("  folders enumerating id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        try (DirectoryStream<Path> directoryStreamOfFolders = Files.newDirectoryStream(path, file -> file.toFile().isDirectory())) {
            int foldersCount = 0;
            for (Path dir : directoryStreamOfFolders) {
                enumeratingDir = dir;
                String relative = appConfig.relativizePathToImport(dir);
                importService.registerNewImportFolderRequest(relative, request, process.getId());
                foldersCount++;
            }
            request.setFoldersCount(foldersCount);
            request.getStats(processType).incFolders(foldersCount);
            requestRepository.save(request);
            log.info("    folders processed {}. id={} status={} path={}",
                    foldersCount,
                    request.getId(), request.getStatus(), request.getPath());
        } catch (IOException e) {
            request.setFoldersCount(-1);
            request.addError("ImportRequest indexing failed for folder "+enumeratingDir);
            requestRepository.save(request);
            log.error(" Failed to index. id={} status={} path={} reason='{}'", request.getId(), request.getStatus(), request.getPath(), e.getMessage(), e);
        }

        log.info("    files enumerating id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        try {
            Path importRootFolder = Paths.get(request.getRootPath());
            int filesCount = 0;
            int filesIgnoredCount = 0;
            int filesSucceedCount = 0;
            ImportRequest.ImportStats stats = request.getStats(processType);
            try (DirectoryStream<Path> directoryStreamOfFiles = Files.newDirectoryStream(path, file -> file.toFile().isFile())) {
                for (Path file : directoryStreamOfFiles) {
                    String fileName = file.toString().toLowerCase();
                    String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1);
                    if (fileName.startsWith(".")) {
                        log.info(" Ignore system file {}", file.toAbsolutePath().toString());
                        continue;
                    }

                    ImportSource info = new ImportSource();
                    info.setImportRequestId(request.getId());
                    info.addIndexProcessId(process.getId());
                    info.setImportRequestRootId(request.getRootId());
                    fileAnalyzer.gatherFileInformation(file, importRootFolder, info);

                    if (imageAnalyzer.acceptsExtension(fileExt)) {
                        filesCount++;
                        stats.incFilesDone();
                        processPictureFile(file, importRootFolder, info);

                        if (!info.isFailed()) {
                            if (!checkIfDuplicate(file, request, info)) {
                                Path targetFolder = importUtils.moveToApprove(file, request.getRootPath());
                                stats.incMovedToApprove();
                                info.setRootPath(targetFolder.toFile().getAbsolutePath());
                                info.setStatus(InfoStatus.ANALYSING);
                                filesSucceedCount++;
                            }
                        } else {
                            filesIgnoredCount++;
                            stats.incFailed();
                            Path targetFolder = importUtils.moveToFailed(file, request.getRootPath());
                            info.setRootPath(targetFolder.toFile().getAbsolutePath());
                            info.setStatus(InfoStatus.FAILED);
                            logUnknownFormats.error(" Unknown format of file {}", file.toAbsolutePath().toString());
                        }
                    } else {
                        Path targetFolder = importUtils.moveToFailed(file, request.getRootPath());
                        info.setRootPath(targetFolder.toFile().getAbsolutePath());
                        stats.incFailed();
                        info.setStatus(InfoStatus.FAILED);
                    }
                    importSourceRepository.saveByGrade(info);
                    if (!InfoStatus.FAILED.equals(info.getStatus()) && !info.hasThumb()) {
                        log.warn("     No thumbnail injected - need to generate one for {} in {}", info.getFileName(), info.getFilePath());
                        Path thumbStoredFile = importUtils.generatePicThumbName(info.getFileName(), info.getTimestamp());
                        String relativeStoredPath = appConfig.relativizePathToThumb(thumbStoredFile.toFile().getAbsolutePath());
                        ThumbRequest request = new ThumbRequest(info.getFullFilePath(), relativeStoredPath);
                        request.setSource(info.getId());
                        thumbRequestRepository.save(request);
                    }
                }
            }

            request.setFilesCount(filesCount);
            request.setFilesIgnoredCount(filesIgnoredCount);
            stats.setAllFilesProcessed(true);
            stats.getFiles().set(filesCount);
            if (stats.getFilesDone().get() != filesCount) {
                log.warn("     Count Mismatch for ImportRequest id={} done={} <> {}",
                        request.getId(), stats.getFilesDone().get() , filesCount);
            }
            stats.getFilesDone().set(filesCount);
            requestRepository.save(request);
            log.info("     files processing done {} or {} succeeded ({} ignored). id={} status={} path={}",
                    filesCount, filesSucceedCount, filesIgnoredCount,
                    request.getId(), request.getStatus(), request.getPath());
        } catch (IOException e) {
            request.setFilesCount(-1);
            request.setFilesIgnoredCount(-1);
            request.getStats(processType).setAllFilesProcessed(true);
            request.addError("indexing failed for file "+path);
            requestRepository.save(request);
            log.error("     import processing failed for file {}. Reason: {}", path, e.getMessage());
        }
        log.warn("   import processing done id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
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
//            info.setStatus(InfoStatus.DUPLICATE);
//            return true;
//        }
        boolean similarFound = importUtils.findSimilar(file.toFile().getName(), file.toFile().length());
        if (similarFound) {
            Path targetFolder = importUtils.moveToSimilar(file, request.getRootPath());
            info.setRootPath(targetFolder.toFile().getAbsolutePath());
            info.setStatus(InfoStatus.SIMILAR);
            return true;
        }
        return false;
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
        newRequest.addIndexProcessId(indexProcessId);
        newRequest.setStatus(INIT);
        newRequest.setPath(path);
        newRequest.setActiveProcessType(ProcessType.IMPORT);
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
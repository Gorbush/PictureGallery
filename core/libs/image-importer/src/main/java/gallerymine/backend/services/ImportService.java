package gallerymine.backend.services;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.PictureFolderRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.data.RetryVersion;
import gallerymine.backend.exceptions.ImageApproveException;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportMatchingRequestPoolManager;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.*;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;
import static gallerymine.model.support.InfoStatus.APPROVED;
import static gallerymine.model.support.InfoStatus.DUPLICATE;
import static gallerymine.model.support.PictureGrade.GALLERY;
import static gallerymine.model.support.PictureGrade.IMPORT;

@Service
public class ImportService {

    private static Logger log = LoggerFactory.getLogger(ImportService.class);

    public static final String KIND_MATCHING = "Matching";

    @Autowired
    private ImportProcessor importProcessor;

    @Autowired
    private ImportUtils importUtils;

    @Autowired
    private PictureFolderRepository pictureFolderRepository;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ImportRequestPoolManager requestImportPool;

    @Autowired
    private ImportApproveRequestPoolManager requestApprovePool;

    @Autowired
    private ImportMatchingRequestPoolManager requestMatchPool;

    @Autowired
    private ProcessRepository processRepository;

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @Autowired
    private UniSourceService uniSourceService;

    @Autowired
    private ProcessService processService;

    @Autowired
    protected ImportRequestRepository requestRepository;

    @Autowired
    protected ImportRequestService requestService;

    @Autowired
    private SourceFilesMatcher sourceFilesMatcher;

    private Map<String, ImportRequest> requestsCache = new HashMap<>();

    public ImportRequest registerNewImportFolderRequest(String path, ImportRequest parent, String indexProcessId) throws ImportFailedException {
        ImportRequest importRequest = importProcessor.registerNewImportFolderRequest(path, parent, indexProcessId);
        requestsCache.put(importRequest.getId(), importRequest);
        return importRequest;
    }

    public void finishRequestProcessing(ImportRequest request) {
        requestsCache.remove(request.getId());
    }

    public ImportRequest getFresh(ImportRequest request) {
        ImportRequest cachedRequest = requestsCache.get(request.getId());
        if (cachedRequest != null && cachedRequest.getUpdated().isAfter(request.getUpdated())) {
            return cachedRequest;
        }
        return request;
    }

    public ImportRequest prepareImportFolder(boolean enforce, boolean testFolder) throws ImportFailedException {
        Process process = new Process();
        ImportRequest importRequest = null;
        try {
            process.setName("Processing of Import"+(testFolder?" of TEST folder":""));
            process.setStatus(ProcessStatus.PREPARING);
            process.setType(ProcessType.IMPORT);
            String importInternalPath = importUtils.prepareImportFolder(enforce, process, testFolder);

            importRequest = registerNewImportFolderRequest(importInternalPath, null, process.getId());
        } catch (ImportFailedException e) {
            process = processService.addError(process.getId(), ProcessStatus.FAILED, "Failed, reason: %s", e.getMessage());
        } catch (Exception e) {
            process = processService.addError(process.getId(), ProcessStatus.FAILED, "Failed, reason: %s", e.getMessage());
        }
        if (importRequest != null) {
            requestImportPool.executeRequest(importRequest);
        }

        return importRequest;
    }

    public void checkIfMatchNeeded(ImportRequest request, Process process) {
        if (process != null) {
            long toMatch = request.getTotalStats(process.getType()).getMovedToApprove().get();
            Process processOfMatching = null;
            if (toMatch > 0) {
                log.info("Matching is needed for {} images for id={}", toMatch, process.getId());
                processOfMatching = new Process();
                processOfMatching.setName("Processing of Matching");
                processOfMatching.setStatus(ProcessStatus.PREPARING);
                processOfMatching.setType(ProcessType.MATCHING);
                processOfMatching.setParentProcessId(process.getId());
                processOfMatching.addNote("%d images for matching", toMatch);

                processRepository.save(processOfMatching);

                process = processService.addNote(process.getId(), "Matching process initiated %s", processOfMatching.getId());

                // update ImportRequests to TO_MATCH with processOfMatching
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfMatching.getId(), ENUMERATION_COMPLETE, TO_MATCH, ProcessType.MATCHING);
                uniSourceRepository.updateAllImportSourcesToNextProcess(process.getId(), processOfMatching.getId());

                requestMatchPool.executeRequest(request);
                log.info(" initiated Matching id={} from Import id={}", processOfMatching.getId(), process.getId());
            } else {
                process = processService.addNote(process.getId(), "Matching process not required");
            }

        } else {
            log.info("Process not found imports={} importRequest={}", request.getIndexProcessIds(), request.getId());
        }
    }

    public void replaceApprovedFilesWithSymLinks(Process process) {
        SourceCriteria criteria = new SourceCriteria();
        criteria.setProcessId(process.getId());
        criteria.addStatuses(InfoStatus.APPROVED);
        criteria.maxSize();

        Iterator<PictureInformation> iterator = uniSourceRepository.fetchCustomStream(criteria, IMPORT.getEntityClass());
        iterator.forEachRemaining( importInfo -> {
            String pictureId = importInfo.getPictureId();
            if (pictureId == null) {
                log.error("ImportSource id={} is marked as APPROVED, but no picture mapped in Gallery! path={}", importInfo.getId(), importInfo.getFileWithPath());
                return;
            }
            PictureInformation pictureInfo;
            try {
                pictureInfo = uniSourceRepository.fetchOne(pictureId, PictureGrade.GALLERY.getEntityClass());
                if (pictureId == null) {
                    log.error("ImportSource id={} is marked as APPROVED, but picture mapped in Gallery is missing! picId={} path={}", importInfo.getId(), pictureId, importInfo.getFileWithPath());
                    return;
                }
            } catch (Exception e) {
                log.error("ImportSource id={} is marked as APPROVED, but picture mapped in Gallery is missing! picId={} path={}", importInfo.getId(), pictureId, importInfo.getFileWithPath());
                return;
            }
            Path impPath = importUtils.calcCompleteFilePath(IMPORT, importInfo.getFullFilePath());
            Path picPath = importUtils.calcCompleteFilePath(GALLERY, pictureInfo.getFileWithPath());
            if (Files.isSymbolicLink(impPath)) {
                log.info("ImportSource id={} is marked as APPROVED, and file is symlink. picId={} path={}", importInfo.getId(), pictureId, importInfo.getFileWithPath());
                return;
            }
            if (!picPath.toFile().exists()) {
                log.error("ImportSource id={} is marked as APPROVED, but picture mapped in Gallery is missing! picId={} path={} picPath={}", importInfo.getId(), pictureId, importInfo.getFileWithPath(), picPath);
                return;
            }
            if (!impPath.toFile().delete()) {
                log.error("ImportSource id={} is marked as APPROVED, but failed to delete approved file! picId={} path={} picPath={}", importInfo.getId(), pictureId, impPath, picPath);
                return;
            }
            try {
                Files.createSymbolicLink(impPath, picPath);
            } catch (IOException e) {
                log.error("ImportSource id={} is marked as APPROVED, but failed to create symlink! picId={} path={} piPath={}", importInfo.getId(), pictureId, impPath, picPath);
            }
        });

    }

    public void checkIfApproveNeeded(ImportRequest request, Process process) {
        if (process != null) {
            long toApprove = request.getTotalStats(process.getType()).getMovedToApprove().get();
            if (toApprove > 0) {
                log.info("Approve is needed for {} images for id={}", toApprove, process.getId());
                Process processOfApprove = new Process();
                processOfApprove.setName("Processing of Approval");
                processOfApprove.setStatus(ProcessStatus.PREPARING);
                processOfApprove.setType(ProcessType.APPROVAL);
                processOfApprove.setParentProcessId(process.getId());
                processOfApprove.addNote("%d images for approval", toApprove);

                processRepository.save(processOfApprove);

                process = processService.addNote(process.getId(), "Approve process initiated %s", processOfApprove.getId());

                // update ImportRequests to TO_APPROVE with processOfApprove
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfApprove.getId(), MATCHING_COMPLETE, TO_APPROVE, ProcessType.APPROVAL);
                uniSourceRepository.updateAllImportSourcesToNextProcess(process.getId(), processOfApprove.getId());

                requestApprovePool.executeRequest(request);

                log.info(" initiated Approve id={} from Matching id={}", processOfApprove.getId(), process.getId());
            } else {
                processService.addNote(process.getId(), "Approve process  not required");
            }

        } else {
            log.info(" Process not found id={} importRequest={}", request.getActiveProcessId(), request.getId());
        }
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public void checkSubsAndDone(String requestId, ImportRequest child, ProcessType processType, ImportRequest.ImportStatus processingDoneStatus) throws ImportFailedException {
        if (requestId == null) {
            log.error(" checkSubsAndDone failed: Failed to check subs for request null");
            return;
        }

        ImportRequest request = requestRepository.findOne(requestId);
        if (request == null) {
            log.error(" checkSubsAndDone failed: Request not found, failed to check subs for id={}", requestId);
            return;
        }
        log.info(" checkSubsAndDone for id={} path={}", requestId, request.getPath());
        if (child != null) {
            if (child.getStatus().isFinal()) {
                request = requestService.appendSubStats(requestId, processType, child);
            } else {
                log.error("  Adding child substats from id={} path={} to parent={} while child is not FINISHED", child.getId(), child.getPath(), requestId);
            }
        }
        ImportRequest.ImportStats stats = request.getStats(processType);
        boolean allSubTasksDone = stats.getFolders().get() == stats.getFoldersDone().get();

        if (!allSubTasksDone)  {
            log.info(" checkSubsAndDone exit id={} path={} Not all subtasks are done", requestId, request.getPath());
            return;
        } else {
            request = requestService.markAllFoldersProcessed(requestId, processType);
        }

        boolean isAllFilesProcessed = stats.getAllFilesProcessed();
        if (!isAllFilesProcessed && stats.getFiles().get() >= 0) {
            isAllFilesProcessed = stats.checkAllFilesProcessed();
            if (isAllFilesProcessed) {
                request = requestService.markAllFilesProcessed(requestId, processType);
            }
        }

        if (!isAllFilesProcessed)  {
            log.info(" checkSubsAndDone exit id={} path={} Not all files are processed", requestId, request.getPath());
            return;
        }

        log.info("  checkSubsAndDone passes id={} path={} could be marked as done", requestId, request.getPath());

        ImportRequest.ImportStatus currentStatus = request.getStatus();
        request = requestService.updateStatus(requestId, processingDoneStatus);

        log.info("   status changed id={} path={} oldStatus={} status={}",
                request.getId(), request.getPath(), currentStatus, request.getStatus());
        if (StringUtils.isNotBlank(request.getParent())) {
            log.info("   processing parent of id={} path={}, parent={}", requestId, request.getPath(), request.getParent());
            checkSubsAndDone(request.getParent(), request, processType, processingDoneStatus);
            log.info("   processing parent done of id={} path={}, parent={}", requestId, request.getPath(), request.getParent());
        }

        finishRequestProcessing(request);

        log.info("   finished id={} path={} status={}", requestId, request.getPath(), request.getStatus());

        if (!request.getIndexProcessIds().isEmpty() &&  // has process
                StringUtils.isBlank(request.getParent())) { // this is the top import request
            Process process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), processType);
            log.info("   finishing process id={} path={} processId={} processType={}",
                    requestId, request.getPath(), process.getId(), process.getType());
            // First do all needed operations
            onRootImportFinished(request, process);
            // Then - mark as finished.
            finishProcess(request, process);
            // TODO: If first failed - we will be able to find abandoned process and finish it
            // as part of pool job
        }
        log.info(" checkSubsAndDone complete for id={} path={}", requestId, request.getPath());
    }

    private void updateMarker(ImportRequest request) {
        MDC.put("marker", request.marker());
    }

    private void onRootImportFinished(ImportRequest request, Process process) throws ImportFailedException {
        switch (process.getType()) {
            case IMPORT: {
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), null, ENUMERATED, ENUMERATION_COMPLETE, ProcessType.MATCHING);
                checkIfMatchNeeded(request, process);
                break;
            }
            case MATCHING: {
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), null, MATCHED, MATCHING_COMPLETE, ProcessType.APPROVAL);
                checkIfApproveNeeded(request, process);
                break;
            }
            case APPROVAL: {
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), null, ImportRequest.ImportStatus.APPROVED, APPROVAL_COMPLETE, null);
                replaceApprovedFilesWithSymLinks(process);
                break;
            }
            default: {
                throw new ImportFailedException("Unknow process type for processing!");
            }
        }
    }

    protected Process finishProcess(ImportRequest request, Process process) {
        ProcessStatus oldStatus = process.getStatus();
        log.info(" updating process of id={} process={} oldStatus={} rootImportStatus={}",
                request.getId(), process.getId(), oldStatus, request.getStatus());
        process = processService.retrySave(process.getId(), progressEntity -> {
            if (request.getStatus().isInProgress()) {
                progressEntity.addError("Import failed");
                progressEntity.setStatus(ProcessStatus.FAILED);
            } else {
                progressEntity.addNote("Import finished");
                progressEntity.setStatus(ProcessStatus.FINISHED);
            }
            addImportStats(progressEntity, request);
            return progressEntity;
        });
        log.info(" Process finished of id={} process={} oldStatus={} status={}",
                request.getId(), process.getId(), oldStatus, process.getStatus());
        return process;
    }

    public void addImportStats(Process process, ImportRequest request) {
        try {
            ImportRequest.ImportStats stats = request.getTotalStats(process.getType());
            process.addNote("Import statistics:");
            process.addNote(" Folders %d of %d", stats.getFoldersDone().get(), stats.getFolders().get());
            process.addNote(" Files in total %7d", stats.getFiles().get());
            process.addNote("   Approve      %7d", stats.getMovedToApprove().get());
            process.addNote("   Failed       %7d", stats.getFailed().get());
            process.addNote("   Similar      %7d", stats.getSimilar().get());
            process.addNote("   Duplicates   %7d", stats.getDuplicates().get());
            process.addNote("   Skipped      %7d", stats.getSkipped().get());
        } catch (Exception e) {
            log.error("Failed to add Statistics", e);
        }
    }

    /** Check if file exists - and if it is - add hash and index like
     * <b>filename#1.ext</b> */
    public Path indexateFileIfNeeded(Path file) {
        int index = 0;
        Path folder = file.getParent();
        String fileName = FilenameUtils.getBaseName(file.toFile().getName());
        String fileExt = FilenameUtils.getExtension(file.toFile().getName());
        while(file.toFile().exists()) {
            file = folder.resolve(fileName+"#"+index+"."+fileExt);
            index++;
        }
        return file;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public PictureFolder getOrCreatePictureFolder(Path folder) throws ImportFailedException {
        try {
            if (folder == null) {
                PictureFolder picFolder = pictureFolderRepository.findByFullPath("");
                if (picFolder == null) {
                    picFolder = new PictureFolder();
                    picFolder.setName("");
                    picFolder.setPath(null);
                    picFolder.setFullPath("");
                    pictureFolderRepository.save(picFolder);
                }
                return picFolder;
            }
            String folderRelPath = folder.toString().toLowerCase();
            PictureFolder picFolder = pictureFolderRepository.findByFullPath(folderRelPath.toLowerCase());

            if (picFolder == null) {
                picFolder = new PictureFolder();
                picFolder.setName(folder.toFile().getName());
                if (folder.getParent() != null) {
                    picFolder.setPath(folder.getParent().toString().toLowerCase());
                } else {
                    picFolder.setPath("");
                }
                picFolder.setFullPath(folder.toString().toLowerCase());

                PictureFolder picFolderParent = getOrCreatePictureFolder(folder.getParent());
                picFolder.setParentId(picFolderParent.getId());

                pictureFolderRepository.save(picFolder);
                if (picFolder.getParentId() != null) {
                    pictureFolderRepository.incrementFoldersCount(picFolder.getParentId());
                }
            }

            return picFolder;
        } catch (Exception e) {
            log.error("Failed to create PictureFolder for path {}. Reason: {}", folder, e.getMessage(), e);
            throw new ImportFailedException("Failed to create PictureFolder for path {}. Reason: {}", folder, e.getMessage());
        }
    }

    public PictureInformation settlePicture(PictureInformation source) throws ImageApproveException {
        try {
            log.info("  source id={} is getting approved", source.getId());

            Path importImage = importUtils.calcCompleteFilePath(source);

            Path relativePathWithFile = source.getFileWithPathAsPath();
            Path relativePathWithFileLowered = Paths.get(relativePathWithFile.toString().toLowerCase());
            Path galleryImagePath = indexateFileIfNeeded(importUtils.calcCompleteFilePath(GALLERY, relativePathWithFileLowered.toString()));

            galleryImagePath.getParent().toFile().mkdirs();

            FileUtils.copyFile(importImage.toFile(), galleryImagePath.toFile(), true);
            PictureFolder picFolder = getOrCreatePictureFolder(relativePathWithFile.getParent());

            Picture picture = uniSourceService.retrySave(source.getId(), Picture.class, pic -> {
                if (pic == null) {
                    pic = new Picture();
                    pic.copyFrom(source);
                    pic.setStatus(APPROVED);
                }
                pic.setGrade(PictureGrade.GALLERY);
                pic.addImport(source.getId());
                pic.setRootPath(null);
                // update to lowered path
                if (relativePathWithFileLowered.getParent() != null) {
                    pic.setFilePath(relativePathWithFileLowered.getParent().toString());
                } else {
                    pic.setFilePath("");
                }
                // update to the lowered and potentially indexed file name
                pic.setFileName(galleryImagePath.toFile().getName());
                pic.setFolderId(picFolder.getId());
                return pic;
            });
            pictureFolderRepository.incrementFilesCount(picFolder.getId());
            log.info("  source id={} is saved to gallery as path={}", source.getId(), relativePathWithFileLowered);

            return picture;
        } catch (Exception e) {
            throw new ImageApproveException("Failed to copy file", e);
        }
    }

    public boolean actionApprove(PictureInformation source) throws Exception {
        log.info("approve for image id={} status={}", source.getId(), source.getStatus());

        String pictureId;
        PictureInformation picture = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (picture == null) {
            log.info("  source id={} is getting approved", source.getId());
            picture = settlePicture(source);
            pictureId = picture.getId();
        } else {
            log.info("  source id={} was already saved as picture", source.getId());
            pictureId = picture.getId();
        }

        InfoStatus oldStatus = source.getStatus();
            if (APPROVED.equals(oldStatus)) {
            log.info("  source id={} is already approved", source.getId());
            return true;
        }

        Path newRootPath = importUtils.moveToApprove(source);
        source = uniSourceService.retrySave(source.getId(), source.getGrade().getEntityClass(), sourceEntity -> {
            sourceEntity.setStatus(APPROVED);
            sourceEntity.setAssignedToPicture(true);
            sourceEntity.addPicture(pictureId);
            sourceEntity.setRootPath(newRootPath.toString());
            return sourceEntity;
        });

        log.info("  source id={} is approved", source.getId());
        ImportRequest request = requestRepository.findOne(source.getImportRequestId());
        if (request == null) {
            log.info("  source id={} is missing import request requestId={}", source.getId(), source.getImportRequestId());
            throw new Exception("Import Request not found for this picture");
        }
        Process process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), ProcessType.APPROVAL);
        if (process == null) {
            log.info("  source id={} is missing import process processIds={}", source.getId(), request.getIndexProcessIds());
            throw new Exception("Import Process not found for this picture");
        }

        request = requestService.retrySave(request.getId(), requestEntity -> {
                    // Updating stats for Import Request and propagate to parent
                    if (!oldStatus.isFinalStatus()) {
                        requestEntity.getStats(ProcessType.APPROVAL).incFilesDone();
                    }
                    requestEntity.getStats(ProcessType.APPROVAL).incMovedToApprove();
                    if (DUPLICATE.equals(oldStatus)) {
                        requestEntity.getStats(ProcessType.APPROVAL).getDuplicates().decrementAndGet();
                    }
                    return requestEntity;
                });

        checkSubsAndDone(request.getId(), null, ProcessType.APPROVAL, ImportRequest.ImportStatus.APPROVED);

        return true;
    }

    public Boolean actionMarkAsDuplicate(PictureInformation source) throws Exception {
        PictureInformation pictureApproved = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (pictureApproved != null) {
            log.info("  source id={} was approved - removing", source.getId());
            uniSourceRepository.deleteByGrade(pictureApproved.getId(), GALLERY.getEntityClass());
            pictureFolderRepository.decrementFilesCount(pictureApproved.getFolderId());
        }
        InfoStatus oldStatus = source.getStatus();

        if (DUPLICATE.equals(oldStatus)) {
            log.info("  source id={} is already marked as Duplicate", source.getId());
            return true;
        }

        Path newRootPath = importUtils.moveToDuplicates(source);
        source = uniSourceService.retrySave(source.getId(), ImportSource.class, sourceEntity -> {
            sourceEntity.setStatus(DUPLICATE);
            sourceEntity.setRootPath(newRootPath.toString());
            return sourceEntity;
        });

        log.info("  source id={} marked as Duplicate", source.getId());

        ImportRequest request = requestRepository.findOne(source.getImportRequestId());
        if (request == null) {
            log.info("  source id={} is missing import request requestId={}", source.getId(), source.getImportRequestId());
            throw new Exception("Import Request not found for this picture");
        }
        Process process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), ProcessType.APPROVAL);
        if (process == null) {
            log.info("  source id={} is missing import process processIds={}", source.getId(), request.getIndexProcessIds());
            throw new Exception("Import Process not found for this picture");
        }

        request = requestService.retrySave(request.getId(), requestEntity -> {
            // Updating stats for Import Request and propagate to parent
            if (!oldStatus.isFinalStatus()) {
                requestEntity.getStats(ProcessType.APPROVAL).incFilesDone();
            }
            requestEntity.getStats(ProcessType.APPROVAL).incDuplicates();
            if (APPROVED.equals(oldStatus)) {
                requestEntity.getStats(ProcessType.APPROVAL).getMovedToApprove().decrementAndGet();
            }
            return requestEntity;
        });

        checkSubsAndDone(request.getId(), null, ProcessType.APPROVAL, ImportRequest.ImportStatus.APPROVED);

        return true;
    }

    public boolean approveImportRequest(ImportRequest request, boolean tentativeAlso, boolean subFolders) {
        if (!request.getStats(ProcessType.APPROVAL).getAllFilesProcessed()) {
            log.warn("  Approving files for requestId={} status={}", request.getId(), request.getStatus());
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.addStatuses(InfoStatus.APPROVING);
            if (tentativeAlso) {
                criteria.addStatuses(InfoStatus.SIMILAR);
            }
            criteria.maxSize();

            Iterator<PictureInformation> iterator = uniSourceRepository.fetchCustomStream(criteria, IMPORT.getEntityClass());
            List<String> errors = new ArrayList<>();
            AtomicLong approved = new AtomicLong();
            AtomicLong notApproved = new AtomicLong();
            iterator.forEachRemaining(importSource -> {
                try {
                    if (actionApprove(importSource)) {
                        approved.incrementAndGet();
                    } else {
                        notApproved.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Failed to approve import={}", importSource.getId(),e);
                    errors.add(String.format("Failed to approve image %s", importSource.getFileWithPath()));
                }
            });
            requestService.retrySave(request.getId(), requestEntity -> {
                requestEntity.addNote("Approved %d files%s", approved.get(),
                        notApproved.get() == 0 ? "" : (", "+notApproved.get()+" not approved"));
                if (errors.size() > 20) {
                    requestEntity.addError("Too many errors occurred during approving: %s. Showing only first 20:", errors.size());
                    requestEntity.getErrors().addAll(errors.subList(0, 19));
                }
                return requestEntity;
            });
        }
        if (subFolders && !request.getStats(ProcessType.APPROVAL).getAllFoldersProcessed()) {
            log.warn("  Approving folders for requestId={} status={}", request.getId(), request.getStatus());
            Stream<ImportRequest> iterator = requestRepository.findByParentForApprove(request.getId());

            List<String> errors = new ArrayList<>();
            AtomicLong approved = new AtomicLong();
            AtomicLong notApproved = new AtomicLong();
            iterator.forEach(importRequest -> {
                try {
                    if (approveImportRequest(importRequest, tentativeAlso, subFolders)) {
                        approved.incrementAndGet();
                    } else {
                        notApproved.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Failed to approve request={}", importRequest.getId(),e);
                    errors.add(String.format("Failed to approve sub-request %s", importRequest.getPath()));
                }
            });
            requestService.retrySave(request.getId(), requestEntity -> {
                requestEntity.addNote("Approved %d sub-requests%s", approved.get(),
                        notApproved.get() == 0 ? "" : (", "+notApproved.get()+" not approved"));
                if (errors.size() > 20) {
                    requestEntity.addError("Too many errors occurred during approving: %s. Showing only first 20:", errors.size());
                    requestEntity.getErrors().addAll(errors.subList(0, 19));
                }
                return requestEntity;
            });
        }
        request = requestRepository.findOne(request.getId());
        return ImportRequest.ImportStatus.APPROVED.equals(request.getStatus());
    }

    public boolean rematchImportRequest(ImportRequest request, boolean tentativeAlso, boolean subFolders) {
        if (!request.getStats(ProcessType.APPROVAL).getAllFilesProcessed()) {
            log.warn("  Matching files for requestId={} status={}", request.getId(), request.getStatus());
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.addStatuses(InfoStatus.APPROVING);
            if (tentativeAlso) {
                criteria.addStatuses(InfoStatus.SIMILAR);
            }
            criteria.maxSize();

            Iterator<PictureInformation> iterator = uniSourceRepository.fetchCustomStream(criteria, IMPORT.getEntityClass());
            List<String> errors = new ArrayList<>();
            AtomicLong approved = new AtomicLong();
            AtomicLong notApproved = new AtomicLong();
            iterator.forEachRemaining(infoImg -> {
                try {
                    SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(infoImg);

                    infoImg = uniSourceService.retrySave(infoImg.getId(), ImportSource.class, info -> {
                        info.setMatchReport(matchReport);
                        info.getPopulatedBy().add(KIND_MATCHING);
                        info.setStatus(InfoStatus.APPROVING);
                        return info;
                    });
                } catch (Exception e) {
                    log.warn("Failed to match import={}", infoImg.getId(),e);
                    errors.add(String.format("Failed to match image %s", infoImg.getFileWithPath()));
                }
            });
            requestService.retrySave(request.getId(), requestEntity -> {
                requestEntity.addNote("Matched %d files%s", approved.get(),
                        notApproved.get() == 0 ? "" : (", "+notApproved.get()+" not matched"));
                if (errors.size() > 20) {
                    requestEntity.addError("Too many errors occurred during matching: %s. Showing only first 20:", errors.size());
                    requestEntity.getErrors().addAll(errors.subList(0, 19));
                }
                return requestEntity;
            });
        }
        if (subFolders && !request.getStats(ProcessType.APPROVAL).getAllFoldersProcessed()) {
            log.warn("  Matching folders for requestId={} status={}", request.getId(), request.getStatus());
            Stream<ImportRequest> iterator = requestRepository.findByParentForApprove(request.getId());

            List<String> errors = new ArrayList<>();
            AtomicLong approved = new AtomicLong();
            AtomicLong notApproved = new AtomicLong();
            iterator.forEach(importRequest -> {
                try {
                    if (rematchImportRequest(importRequest, tentativeAlso, subFolders)) {
                        approved.incrementAndGet();
                    } else {
                        notApproved.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("Failed to match request={}", importRequest.getId(),e);
                    errors.add(String.format("Failed to match sub-request %s", importRequest.getPath()));
                }
            });
            requestService.retrySave(request.getId(), requestEntity -> {
                requestEntity.addNote("Matched %d sub-requests%s", approved.get(),
                        notApproved.get() == 0 ? "" : (", "+notApproved.get()+" not approved"));
                if (errors.size() > 20) {
                    requestEntity.addError("Too many errors occurred during approving: %s. Showing only first 20:", errors.size());
                    requestEntity.getErrors().addAll(errors.subList(0, 19));
                }
                return requestEntity;
            });
        }
        return true;
    }
}

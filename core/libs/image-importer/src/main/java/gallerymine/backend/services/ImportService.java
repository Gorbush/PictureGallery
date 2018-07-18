package gallerymine.backend.services;

import com.drew.tools.FileUtil;
import com.google.common.collect.Sets;
import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.data.RetryVersion;
import gallerymine.backend.exceptions.ImageApproveException;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportMatchingRequestPoolManager;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.Picture;
import gallerymine.model.PictureInformation;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
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

    @Autowired
    private ImportProcessor importProcessor;

    @Autowired
    private ImportUtils importUtils;

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

    public ImportRequest prepareImportFolder(boolean enforce) throws ImportFailedException {
        Process process = new Process();
        ImportRequest importRequest = null;
        try {
            process.setName("Processing of Import");
            process.setStatus(ProcessStatus.PREPARING);
            process.setType(ProcessType.IMPORT);
            String importInternalPath = importUtils.prepareImportFolder(enforce, process);

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

                requestMatchPool.executeRequest(request);
                log.info(" initiated Matching id={} from Import id={}", processOfMatching.getId(), process.getId());
            } else {
                process = processService.addNote(process.getId(), "Matching process not required");
            }

        } else {
            log.info("Process not found imports={} importRequest={}", request.getIndexProcessIds(), request.getId());
        }
    }

    public void checkIfApproveNeeded(ImportRequest request, Process process) {
        if (process != null) {
            long toApprove = request.getTotalStats(process.getType()).getMovedToApprove().get();
            Process processOfApprove = null;
            if (toApprove > 0) {
                log.info("Approve is needed for {} images for id={}", toApprove, process.getId());
                processOfApprove = new Process();
                processOfApprove.setName("Processing of Approval");
                processOfApprove.setStatus(ProcessStatus.PREPARING);
                processOfApprove.setType(ProcessType.APPROVAL);
                processOfApprove.setParentProcessId(process.getId());
                processOfApprove.addNote("%d images for approval", toApprove);

                processRepository.save(processOfApprove);

                process = processService.addNote(process.getId(), "Approve process initiated %s", processOfApprove.getId());

                // update ImportRequests to TO_APPROVE with processOfApprove
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfApprove.getId(), MATCHING_COMPLETE, TO_APPROVE, ProcessType.APPROVAL);

                requestApprovePool.executeRequest(request);

                log.info(" initiated Approve id={} from Matching id={}", processOfApprove.getId(), process.getId());
            } else {
                process = processService.addNote(process.getId(), "Approve process  not required");
            }

        } else {
            log.info(" Process not found id={} importRequest={}", process.getId(), request.getId());
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
            process = finishProcess(request, process);
            onRootImportFinished(request, process);
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

    private void addImportStats(Process process, ImportRequest request) {
        try {
            ImportRequest.ImportStats stats = request.getTotalStats(process.getType());
            process.addNote("Import statistics:");
            process.addNote(" Folders %d of %d", stats.getFoldersDone().get(), stats.getFolders().get());
            process.addNote(" Files in total %7d", stats.getFiles().get());
            process.addNote("   Approve      %7d", stats.getMovedToApprove().get());
            process.addNote("   Failed       %7d", stats.getFailed().get());
            process.addNote("   Similar      %7d", stats.getSimilar().get());
            process.addNote("   Duplicates   %7d", stats.getDuplicates().get());
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

    public PictureInformation settlePicture(PictureInformation source) throws ImageApproveException {
        try {
            log.info("  source id={} is getting approved", source.getId());

            Path importImage = importUtils.calcCompleteFilePath(source);
            Path galleryImagePath = indexateFileIfNeeded(importUtils.calcCompleteFilePath(GALLERY, source.getFileWithPath()));

            galleryImagePath.getParent().toFile().mkdirs();

            FileUtils.copyFile(importImage.toFile(), galleryImagePath.toFile(), true);

            Picture picture = uniSourceService.retrySave(source.getId(), Picture.class, pic -> {
                if (pic == null) {
                    pic = new Picture();
                    pic.copyFrom(source);
                    pic.setStatus(APPROVED);
                }
                pic.setGrade(PictureGrade.GALLERY);
                pic.addSource(source.getId(), source.getGrade());
                pic.setRootPath(null);
                pic.setFileName(galleryImagePath.toFile().getName());
                return pic;
            });

            return picture;
        } catch (Exception e) {
            throw new ImageApproveException("Failed to copy file");
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

        source = uniSourceService.retrySave(source.getId(), IMPORT.getEntityClass(), info -> {
            info.setStatus(APPROVED);
            info.setAssignedToPicture(true);
            info.addSource(pictureId, GALLERY);
            return info;
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
        PictureInformation target = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (target != null) {
            log.info("  source id={} was approved - removing", source.getId());
            uniSourceRepository.deleteByGrade(target.getId(), GALLERY.getEntityClass());
        }
        InfoStatus oldStatus = source.getStatus();

        if (DUPLICATE.equals(oldStatus)) {
            log.info("  source id={} is already marked as Duplicate", source.getId());
            return true;
        }

        uniSourceService.updateStatus(source.getId(), IMPORT.getEntityClass(), DUPLICATE);
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

    public boolean approveImportRequest(ImportRequest request, boolean tentativeOnly, boolean subFolders) {
        if (!request.getStats(ProcessType.APPROVAL).getAllFilesProcessed()) {
            log.warn("  Approving files for requestId={} status={}", request.getId(), request.getStatus());
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.setStatus(InfoStatus.APPROVING);
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
                    if (approveImportRequest(importRequest, tentativeOnly, subFolders)) {
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
}

package gallerymine.backend.services;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.data.RetryVersion;
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
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;
import static gallerymine.model.support.InfoStatus.APPROVED;
import static gallerymine.model.support.InfoStatus.DUPLICATE;
import static gallerymine.model.support.PictureGrade.GALLERY;

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
    protected ImportRequestRepository requestRepository;

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
            process.setStatus(ProcessStatus.FAILED);
            processRepository.save(process);
        } catch (Exception e) {
            process.setStatus(ProcessStatus.FAILED);
            process.addError("Failed, reason: %s", e.getMessage());
            processRepository.save(process);
        }
        if (importRequest != null) {
            requestImportPool.executeRequest(importRequest);
        }

        return importRequest;
    }

    public void checkIfMatchNeeded(ImportRequest rootImportRequest, Process process) {
        if (process != null) {
            long toMatch = rootImportRequest.getTotalStats(process.getType()).getMovedToApprove().get();
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

                process.addNote("Matching process initiated %s", processOfMatching.getId());
                processRepository.save(process);
                // update ImportRequests to TO_MATCH with processOfMatching
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfMatching.getId(), ENUMERATION_COMPLETE, TO_MATCH, ProcessType.MATCHING);

                requestMatchPool.executeRequest(rootImportRequest);
                log.info(" initiated Matching id={} from Import id={}", processOfMatching.getId(), process.getId());
            } else {
                process.addNote("Matching process not required");
                processRepository.save(process);
            }

        } else {
            log.info("Process not found imports={} importRequest={}", rootImportRequest.getIndexProcessIds(), rootImportRequest.getId());
        }
    }

    public void checkIfApproveNeeded(ImportRequest rootImportRequest, Process process) {
        if (process != null) {
            long toApprove = rootImportRequest.getTotalStats(process.getType()).getMovedToApprove().get();
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

                process.addNote("Approve process initiated %s", processOfApprove.getId());
                processRepository.save(process);
                // update ImportRequests to TO_APPROVE with processOfApprove
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfApprove.getId(), MATCHING_COMPLETE, TO_APPROVE, ProcessType.APPROVAL);

                requestApprovePool.executeRequest(rootImportRequest);

                log.info(" initiated Approve id={} from Matching id={}", processOfApprove.getId(), process.getId());
            } else {
                process.addNote("Approve process not required");
                processRepository.save(process);
            }

        } else {
            log.info(" Process not found id={} importRequest={}", process.getId(), rootImportRequest.getId());
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
                if (request.appendSubStats(processType, child)) {
                    ImportRequest.ImportStats stats = request.getStats(processType);
                    stats.incFoldersDone();
                    log.info("  Adding child substats folders={} of {} from type={} id={} child={} path={} ",
                            stats.getFoldersDone().get(), stats.getFolders().get(), processType,
                            request.getId(), child.getId(), child.getPath());
                    requestRepository.save(request);
                } else {
                    log.info("  Already added child substats from id={} path={}", child.getId(), child.getPath());
                }
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
            if (!stats.getAllFoldersProcessed()) {
                stats.setAllFoldersProcessed(true);
                requestRepository.save(request);
            }
        }

        boolean isAllFilesProcessed = stats.getAllFilesProcessed();
        if (!isAllFilesProcessed && stats.getFiles().get() >= 0) {
            isAllFilesProcessed = stats.getFiles().get() == stats.getFilesDone().get();
            if (isAllFilesProcessed) {
                stats.setAllFilesProcessed(true);
                requestRepository.save(request);
            }
        }

        if (!isAllFilesProcessed)  {
            log.warn(" checkSubsAndDone exit id={} path={} Not all files are processed", requestId, request.getPath());
            return;
        }

        log.info("  checkSubsAndDone passes id={} path={} could be marked as done", requestId, request.getPath());

        ImportRequest.ImportStatus currentStatus = request.getStatus();

        request.setStatus(processingDoneStatus);

        requestRepository.save(request);
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
            finishProcess(request, process);
            onRootImportFinished(request, process);
        }
        log.info(" checkSubsAndDone complete for id={} path={}", requestId, request.getPath());
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

    protected void finishProcess(ImportRequest rootImportRequest, Process process) {
        ProcessStatus oldStatus = process.getStatus();
        log.info(" updating process of id={} process={} oldStatus={} rootImportStatus={}",
                rootImportRequest.getId(), process.getId(), oldStatus, rootImportRequest.getStatus());
        if (rootImportRequest.getStatus().isInProgress()) {
            process.addError("Import failed");
            process.setStatus(ProcessStatus.FAILED);
        } else {
            process.addNote("Import finished");
            process.setStatus(ProcessStatus.FINISHED);
        }
        addImportStats(process, rootImportRequest);
        log.info(" Process finished of id={} process={} oldStatus={} status={}",
                rootImportRequest.getId(), process.getId(), oldStatus, process.getStatus());
    }

    private void addImportStats(Process process, ImportRequest rootImportRequest) {
        try {
            ImportRequest.ImportStats stats = rootImportRequest.getTotalStats(process.getType());
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

    public Boolean actionApprove(PictureInformation source) throws Exception {
        log.info("approve for image id={} status={}", source.getId(), source.getStatus());

        PictureInformation picture = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (picture == null) {
            log.info("  source id={} is getting approved", source.getId());
            picture = new Picture();
            picture.copyFrom(source);
            picture.setGrade(PictureGrade.GALLERY);
            picture.addSource(source.getId(), source.getGrade());

            uniSourceRepository.saveByGrade(picture);
        } else {
            log.info("  source id={} is already approved", source.getId());
        }

        InfoStatus oldStatus = source.getStatus();
        if (APPROVED.equals(oldStatus)) {
            log.info("  source id={} is already approved", source.getId());
            return true;
        }
        source.setStatus(APPROVED);
        source.setAssignedToPicture(true);
        source.addSource(picture.getId(), picture.getGrade());
        uniSourceRepository.saveByGrade(source);

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

        // Updating stats for Import Request and propagate to parent
        if (!oldStatus.isFinalStatus()) {
            request.getStats(ProcessType.APPROVAL).incFilesDone();
        }
        request.getStats(ProcessType.APPROVAL).incMovedToApprove();
        if (DUPLICATE.equals(oldStatus)) {
            request.getStats(ProcessType.APPROVAL).getDuplicates().decrementAndGet();
        }
        requestRepository.save(request);

        checkSubsAndDone(request.getId(), null, ProcessType.APPROVAL, ImportRequest.ImportStatus.APPROVED);

        return true;
    }

    public Boolean actionMarkAsDuplicate(PictureInformation source) throws Exception {
        PictureInformation target = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (target != null) {
            log.info("  source id={} was approved - removing", source.getId());
            uniSourceRepository.delete(target);
        }
        InfoStatus oldStatus = source.getStatus();

        if (DUPLICATE.equals(oldStatus)) {
            log.info("  source id={} is already marked as Duplicate", source.getId());
            return true;
        }

        source.setStatus(DUPLICATE);
        uniSourceRepository.saveByGrade(source);
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

        // Updating stats for Import Request and propagate to parent
        if (!oldStatus.isFinalStatus()) {
            request.getStats(ProcessType.APPROVAL).incFilesDone();
        }
        request.getStats(ProcessType.APPROVAL).incDuplicates();
        if (APPROVED.equals(oldStatus)) {
            request.getStats(ProcessType.APPROVAL).getMovedToApprove().decrementAndGet();
        }

        requestRepository.save(request);

        checkSubsAndDone(request.getId(), null, ProcessType.APPROVAL, ImportRequest.ImportStatus.APPROVED);
        return true;
    }
}

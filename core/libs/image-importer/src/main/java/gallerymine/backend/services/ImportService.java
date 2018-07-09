package gallerymine.backend.services;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
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
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
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
                log.info("ImportService Matching is needed for {} images", toMatch);
                processOfMatching = new Process();
                processOfMatching.setName("Processing of Matching");
                processOfMatching.setStatus(ProcessStatus.PREPARING);
                processOfMatching.setType(ProcessType.MATCHING);
                processOfMatching.setParentProcessId(process.getId());
                processOfMatching.addNote("%d images for matching", toMatch);

                processRepository.save(processOfMatching);

                process.addNote("ImportService Matching process initiated %s", processOfMatching.getId());
                processRepository.save(process);
                // update ImportRequests to TO_MATCH with processOfMatching
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfMatching.getId(), ENUMERATION_COMPLETE, TO_MATCH);

                requestMatchPool.executeRequest(rootImportRequest);
            } else {
                process.addNote("ImportService Matching process not required");
                processRepository.save(process);
            }

        } else {
            log.info("ImportService Process not found imports={} importRequest={}", rootImportRequest.getIndexProcessIds(), rootImportRequest.getId());
        }
    }

    public void checkIfApproveNeeded(ImportRequest rootImportRequest, Process process) {
        if (process != null) {
            long toApprove = rootImportRequest.getTotalStats(process.getType()).getMovedToApprove().get();
            Process processOfApprove = null;
            if (toApprove > 0) {
                log.info("ImportService Approve is needed for {} images", toApprove);
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
                uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), processOfApprove.getId(), MATCHING_COMPLETE, TO_APPROVE);

                requestApprovePool.executeRequest(rootImportRequest);
            } else {
                process.addNote("Approve process not required");
                processRepository.save(process);
            }

        } else {
            log.info("ImportRequest Process not found id={} importRequest={}", process.getId(), rootImportRequest.getId());
        }
    }

    public Boolean actionApprove(PictureInformation source) {
        log.info("ImportService approve for image id={} kind={}", source.getId());

        PictureInformation target = uniSourceRepository.fetchOne(source.getId(), GALLERY.getEntityClass());
        if (target != null) {
            log.info("  source id={} is already approved", source.getId());
            return true;
        }
        log.info("  source id={} is getting approved", source.getId());

        Picture picture = new Picture();
        picture.copyFrom(source);
        picture.setGrade(PictureGrade.GALLERY);
        picture.addSource(source.getId(), source.getGrade());

        uniSourceRepository.saveByGrade(picture);

        source.setStatus(APPROVED);
        source.setAssignedToPicture(true);
        source.addSource(picture.getId(), picture.getGrade());
        uniSourceRepository.saveByGrade(source);

        log.info("  source id={} is approved", source.getId());
        return true;
    }

    public Boolean actionMarkAsDuplicate(PictureInformation source) {
        if (DUPLICATE.equals(source.getStatus())) {
            log.info("  source id={} is already marked as Duplicate", source.getId());
            return true;
        }
        source.setStatus(DUPLICATE);
        uniSourceRepository.saveByGrade(source);
        log.info("  source id={} marked as Duplicate", source.getId());
        return true;
    }
}

package gallerymine.backend.services;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.importer.ImportProcessor;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

import static gallerymine.model.importer.ImportRequest.ImportStatus.ANALYSIS_COMPLETE;
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
    private ImportRequestPoolManager requestPool;

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
            requestPool.executeRequest(importRequest);
        }

        return importRequest;
    }

    public void checkIfApproveNeeded(ImportRequest rootImportRequest, Process process) {
//        Process process = processRepository.findOne(rootImportRequest.getIndexProcessId());
        if (process != null) {
            ProcessStatus oldStatus = process.getStatus();
            log.info("ImportRequest updating process of id={} process={} oldStatus={} ",
                    rootImportRequest.getId(), rootImportRequest.getIndexProcessId(), oldStatus);
            if (rootImportRequest.getStatus().equals(ImportRequest.ImportStatus.DONE)) {
                process.addNote("Import finished");
                process.setStatus(ProcessStatus.FINISHED);
            } else {
                process.addError("Import failed");
                process.setStatus(ProcessStatus.FAILED);
            }
            addImportStats(process, rootImportRequest);
            log.info("ImportRequest Process finished of id={} process={} oldStatus={} status={}",
                    rootImportRequest.getId(), rootImportRequest.getIndexProcessId(), oldStatus, process.getStatus());

            long toApprove = rootImportRequest.getTotalStats().getMovedToApprove().get();
            Process processOfApprove = null;
            if (toApprove > 0) {
                log.info("ImportRequest Approve is needed for {} images", toApprove);
                processOfApprove = new Process();
                processOfApprove.setName("Processing of Approval");
                processOfApprove.setStatus(ProcessStatus.PREPARING);
                processOfApprove.setType(ProcessType.APPROVAL);
                processOfApprove.setParentProcessId(process.getId());
                processOfApprove.addNote("%d images for approval", toApprove);

                processRepository.save(processOfApprove);

                process.addNote("Approve process initiated %s", processOfApprove.getId());
            } else {
                process.addNote("Approve process not required");
            }
            processRepository.save(process);

            // update all Import Requests to required status
            if (toApprove > 0) {
                // update ImportRequests to TO_MATCH with processOfApprove
                uniSourceRepository.updateAllRequestsToMatch(process.getId());
            } else {
                // update ImportRequests to MATCHING_COMPLETE
            }
        } else {
            log.info("ImportRequest Process not found id={} importRequest={}", rootImportRequest.getIndexProcessId(), rootImportRequest.getId());
        }
    }

    private void addImportStats(Process process, ImportRequest rootImportRequest) {
        try {
            ImportRequest.ImportStats stats = rootImportRequest.getTotalStats();
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

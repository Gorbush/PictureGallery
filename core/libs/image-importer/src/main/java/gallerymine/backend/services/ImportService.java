package gallerymine.backend.services;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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

    public void checkIfApproveNeeded(ImportRequest rootImportRequest) {
        Process process = processRepository.findOne(rootImportRequest.getIndexProcessId());
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
            log.info("ImportRequest Process finished of id={} process={} oldStatus={} status={}",
                    rootImportRequest.getId(), rootImportRequest.getIndexProcessId(), oldStatus, process.getStatus());

            long toApprove = rootImportRequest.getStats().getMovedToApprove().get();
            if (toApprove > 0) {
                log.info("ImportRequest Approve is needed for {} images", toApprove);
                Process processOfApprove = new Process();
                processOfApprove.setName("Processing of Approval");
                processOfApprove.setStatus(ProcessStatus.PREPARING);
                processOfApprove.setType(ProcessType.APPROVAL);
                processOfApprove.setParentProcessId(process.getId());
                processOfApprove.addNote("%d images for approval", toApprove);

                processRepository.save(processOfApprove);

                process.addNote("Approve process initiated %s", processOfApprove.getId());
            }
            processRepository.save(process);

        } else {
            log.info("ImportRequest Process not found id={} importRequest={}", rootImportRequest.getIndexProcessId(), rootImportRequest.getId());
        }
    }

}

package gallerymine.backend.services;

import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.data.RetryRunner;
import gallerymine.backend.data.RetryVersion;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ImportRequestService {

    private static Logger log = LoggerFactory.getLogger(ImportRequestService.class);

    @Autowired
    protected ImportRequestRepository requestRepository;

    public void updateMarker(ImportRequest request) {
        MDC.put("marker", request.marker());
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest retrySave(String entityId, RetryRunner<ImportRequest> runner) {
        ImportRequest request = requestRepository.findOne(entityId);
        if (runner.run(request)) {
            requestRepository.save(request);
            updateMarker(request);
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest appendSubStats(String requestId, ProcessType processType, ImportRequest child) {
        ImportRequest request = requestRepository.findOne(requestId);
        if (request.appendSubStats(processType, child)) {
            ImportRequest.ImportStats stats = request.getStats(processType);
            stats.incFoldersDone();
            log.info("  Adding child substats folders={} of {} from type={} id={} child={} path={} ",
                    stats.getFoldersDone().get(), stats.getFolders().get(), processType,
                    request.getId(), child.getId(), child.getPath());
            requestRepository.save(request);
            updateMarker(request);
        } else {
            log.info("  Already added child substats from id={} path={}", child.getId(), child.getPath());
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest markAllFoldersProcessed(String requestId, ProcessType processType) {
        ImportRequest request = requestRepository.findOne(requestId);
        ImportRequest.ImportStats stats = request.getStats(processType);
        if (!stats.getAllFoldersProcessed()) {
            stats.setAllFoldersProcessed(true);
            requestRepository.save(request);
            updateMarker(request);
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest markAllFilesProcessed(String requestId, ProcessType processType) {
        ImportRequest request = requestRepository.findOne(requestId);
        ImportRequest.ImportStats stats = request.getStats(processType);
        if (!stats.getAllFilesProcessed()) {
            stats.setAllFilesProcessed(true);
            requestRepository.save(request);
            updateMarker(request);
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest updateStatus(String requestId, ImportRequest.ImportStatus newStatus) {
        ImportRequest request = requestRepository.findOne(requestId);
        ImportRequest.ImportStatus status = request.getStatus();
        if (!status.equals(newStatus)) {
            request.setStatus(newStatus);
            requestRepository.save(request);
            updateMarker(request);
            log.info(" ImportRequest status changed old={} path={}", status, request.getPath());
        }
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest addError(String requestId, String error, Object... params) {
        ImportRequest request = requestRepository.findOne(requestId);
        request.addError(error, params);
        requestRepository.save(request);
        updateMarker(request);
        return request;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public ImportRequest addError(String requestId, ImportRequest.ImportStatus status, String error, Object... params) {
        ImportRequest request = requestRepository.findOne(requestId);
        request.setStatus(status);
        request.addError(error, params);
        requestRepository.save(request);
        updateMarker(request);
        return request;
    }
}

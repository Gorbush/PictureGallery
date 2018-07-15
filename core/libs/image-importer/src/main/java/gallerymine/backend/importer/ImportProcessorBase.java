package gallerymine.backend.importer;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.helpers.analyzer.ImageFormatAnalyser;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.ImportRequestService;
import gallerymine.backend.services.ImportService;
import gallerymine.backend.services.ProcessService;
import gallerymine.backend.services.UniSourceService;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

import java.nio.file.Path;

public abstract class ImportProcessorBase implements Runnable {

    private static Logger log = LoggerFactory.getLogger(ImportProcessorBase.class);
    protected static Logger logFailed = LoggerFactory.getLogger("failedToIndexError");
    protected static Logger logUnknownFormats = LoggerFactory.getLogger("failedToIndexUnknown");

    protected ImportApproveRequestPoolManager.StatusHolder statusHolder;

    @Autowired
    protected ThumbRequestRepository thumbRequestRepository;

    @Autowired
    protected AppConfig appConfig;

    @Autowired
    protected ImportUtils importUtils;

    @Autowired
    protected ImportRequestRepository requestRepository;

    @Autowired
    protected ImportRequestService requestService;

    @Autowired
    protected ProcessRepository processRepository;

    @Autowired
    protected ProcessService processService;

    @Autowired
    protected ImportSourceRepository uniSourceRepository;

    @Autowired
    protected UniSourceService uniSourceService;

    @Autowired
    protected ImageFormatAnalyser imageAnalyzer;

    @Autowired
    protected GenericFileAnalyser fileAnalyzer;

    @Autowired
    protected ImportService importService;

    protected ImportRequest request;
    protected Process process;
    protected ImportPoolManagerBase pool;

    protected ProcessType processType;

    public ImportProcessorBase(ImportApproveRequestPoolManager.StatusHolder statuses, ProcessType processType) {
        this.statusHolder = statuses;
        this.processType = processType;
    }

    protected boolean validateImportRequest(Process process, Path path) {
        if (!path.toFile().exists()) {
            String error = request.addError("Path not found for request : %s", path.toFile().getAbsolutePath());
            log.error(" "+error);

            request = requestService.updateStatus(request.getId(), statusHolder.getProcessingDone());

            processService.addError(process.getId(), error);

            return false;
        }
        return true;
    }

    protected void updateMarker() {
        MDC.put("marker", request.marker());
    }

    public void run() {
        updateMarker();
        try {
            log.info(" processing started for {}", request.getPath());
            process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), processType);
            if (process == null) {
                process = new Process();
                process.setName("Pictures Folder Import ? "+this.getClass().getSimpleName());
                process.setType(processType);
            }
            processService.retrySave(process.getId(), process -> {
                process.setStatus(ProcessStatus.STARTED);
                process.setStarted(DateTime.now());
                return true;
            });
            updateMarker();
            processRequest();

            log.info(" processing finished successfuly for {}", request.getPath());
        } catch (Exception e){
            log.error(" processing failed for {} Reason: {}", request.getPath(), e.getMessage(), e);
        } finally {
            MDC.remove("marker");
        }
    }

    protected ImportRequest checkRequest() {
        request = requestRepository.findOne(request.getId());
        if (request == null) {
            log.info(" not found for id={} and path={}", request.getId(), request.getPath());
            return null;
        }
        if (!statusHolder.getAwaitingProcessing().equals(request.getStatus())) {
            log.info(" status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        return request;
    }

    public abstract void requestProcessing() throws ImportFailedException;

    public void processRequest() {
        log.info(" picked up path={}", request.getPath());
        if (checkRequest() == null) {
            log.info(" skipped id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return;
        }
        log.info("  processing started id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        try {
            requestProcessing();

            importService.checkSubsAndDone(request.getId(), null, process.getType(), statusHolder.getProcessingDone());
        } catch (OptimisticLockingFailureException e) {
            requestService.addError(request.getId(), statusHolder.getFinished(), e.getMessage());
            importService.finishRequestProcessing(request);
            log.error("   processing failed OptimisticLockingFailureException path={}", request.getPath(), e);
        } catch (Exception e) {
            requestService.addError(request.getId(), statusHolder.getFinished(), e.getMessage());
            importService.finishRequestProcessing(request);
            log.error("   processing failed path={}", request.getPath(), e);
        } finally {
            log.info("  processing done id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            pool.checkForAwaitingRequests();
        }
    }

    public void setRequest(ImportRequest request) {
        this.request = request;
    }

    public void setPool(ImportPoolManagerBase pool) {
        this.pool = pool;
    }
}
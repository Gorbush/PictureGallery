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
import gallerymine.backend.services.ImportService;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
    protected ProcessRepository processRepository;

    @Autowired
    protected ImportSourceRepository sourceRepository;

    @Autowired
    protected ImageFormatAnalyser imageAnalyzer;

    @Autowired
    protected GenericFileAnalyser fileAnalyzer;

    @Autowired
    protected ImportService importService;

    protected ImportRequest request;
    protected ImportPoolManagerBase pool;

    protected ProcessType processType;

    public ImportProcessorBase(ImportApproveRequestPoolManager.StatusHolder statuses, ProcessType processType) {
        this.statusHolder = statuses;
        this.processType = processType;
    }

    protected boolean validateImportRequest(Process process, Path path) {
        if (!path.toFile().exists()) {
            String error = request.addError("Path not found for request : %s", path.toFile().getAbsolutePath());
            log.error(this.getClass().getSimpleName()+" "+error);

            request.setStatus(statusHolder.getProcessingDone());
            requestRepository.save(request);
            process.addError(error);
            processRepository.save(process);

            return false;
        }
        return true;
    }

    public void run() {
        Process process = null;
        try {
            log.info(this.getClass().getSimpleName()+" processing started for {}", request.getPath());
            process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), processType);
            if (process == null) {
                process = new Process();
                process.setName("Pictures Folder Import ? "+this.getClass().getSimpleName());
                process.setType(processType);
            }
            process.setStatus(ProcessStatus.STARTED);
            process.setStarted(DateTime.now());
            processRepository.save(process);

            processRequest(request, process);

            log.info(this.getClass().getSimpleName()+" processing started successfuly for {}", request.getPath());
        } catch (Exception e){
            log.error(this.getClass().getSimpleName()+" processing failed for {} Reason: {}", request.getPath(), e.getMessage(), e);
        }
    }

    protected ImportRequest checkRequest(ImportRequest requestSrc) {
        ImportRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info(this.getClass().getSimpleName()+" not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!statusHolder.getAwaitingProcessing().equals(request.getStatus())) {
            log.info(this.getClass().getSimpleName()+" status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

//        request.setStatus(statusHolder.getAwaitingProcessing());
//        requestRepository.save(request);
//        log.info(this.getClass().getSimpleName()+" status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        return request;
    }

    public abstract void requestProcessing(ImportRequest requestSrc, Process process) throws ImportFailedException;

    public void processRequest(ImportRequest requestSrc, Process process) {
        log.info(this.getClass().getSimpleName()+" picked up id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
        ImportRequest request = checkRequest(requestSrc);
        if (request == null) {
            log.info(this.getClass().getSimpleName()+" skipped id={} status={} path={}", requestSrc.getId(), requestSrc.getStatus(), requestSrc.getPath());
            return;
        }
        log.info(this.getClass().getSimpleName()+" started processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        try {
            requestProcessing(requestSrc, process);

            importService.checkSubsAndDone(request.getId(), null, process.getType(), statusHolder.getProcessingDone());
        } catch (Exception e) {
            request.setStatus(statusHolder.getFinished());
            request.addError(e.getMessage());
            requestRepository.save(request);
            importService.finishRequestProcessing(request);
            log.info(this.getClass().getSimpleName()+" status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        } finally {
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
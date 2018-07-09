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

            checkSubsAndDone(request.getId(), null);
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

    protected void checkSubsAndDone(String requestId, ImportRequest child) {
        if (requestId == null) {
            log.error(this.getClass().getSimpleName()+" Failed to check subs for request id={}", requestId);
            return;
        }

        ImportRequest request = requestRepository.findOne(requestId);
        if (request == null) {
            log.error(this.getClass().getSimpleName()+" Request not found, failed to check subs for id={}", requestId);
            return;
        }

        if (child != null) {
            if (child.getStatus().isFinal()) {
                log.info(this.getClass().getSimpleName()+" Adding child substats from id={}", child.getId());
                request.getStats().incFoldersDone();
                request.getSubStats().append(child.getTotalStats());
                requestRepository.save(request);
            } else {
                log.error(this.getClass().getSimpleName()+" Adding child substats from id={} to parent={} while child is not FINISHED", child.getId(), requestId);
            }
        }

        boolean allSubTasksDone = request.getStats().getFolders().get() == request.getStats().getFoldersDone().get();

        if (!allSubTasksDone)  {
            log.debug(this.getClass().getSimpleName()+" id={} Not all subtasks are done", requestId);
            return;
        } else {
            if (!request.getAllFoldersProcessed()) {
                request.setAllFoldersProcessed(true);
                requestRepository.save(request);
            }
        }

        boolean isAllFilesProcessed = request.getAllFilesProcessed();

        if (!isAllFilesProcessed)  {
            log.debug(this.getClass().getSimpleName()+" id={} Not all files are processed", requestId);
            return;
        }

        ImportRequest.ImportStatus currentStatus = request.getStatus();

        boolean someErrors = request.getTotalStats().getFailed().get() > 0;

        request.setStatus(statusHolder.getProcessingDone());

        requestRepository.save(request);
        log.info(this.getClass().getSimpleName()+" status changed id={} oldStatus={} status={} path={}",
                request.getId(), currentStatus, request.getStatus(), request.getPath());
        if (StringUtils.isNotBlank(request.getParent())) {
            log.info(this.getClass().getSimpleName()+" processing parent of id={} parent={}", requestId, request.getParent());
            checkSubsAndDone(request.getParent(), request);
        }

        importService.finishRequestProcessing(request);

        log.info(this.getClass().getSimpleName()+" finished id={} status={}", requestId, request.getStatus());

        if (!request.getIndexProcessIds().isEmpty() &&  // has process
            StringUtils.isBlank(request.getParent())) { // this is the top import request
            Process process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), processType);
            finishProcess(request, process);
            onRootImportFinished(request, process);
        }
    }

    protected void finishProcess(ImportRequest rootImportRequest, Process process) {
        ProcessStatus oldStatus = process.getStatus();
        log.info(this.getClass().getSimpleName()+" updating process of id={} process={} oldStatus={} rootImportStatus={}",
                rootImportRequest.getId(), process.getId(), oldStatus, rootImportRequest.getStatus());
        if (rootImportRequest.getStatus().isInProgress()) {
            process.addError("Import failed");
            process.setStatus(ProcessStatus.FAILED);
        } else {
            process.addNote("Import finished");
            process.setStatus(ProcessStatus.FINISHED);
        }
        addImportStats(process, rootImportRequest);
        log.info(this.getClass().getSimpleName()+" Process finished of id={} process={} oldStatus={} status={}",
                rootImportRequest.getId(), process.getId(), oldStatus, process.getStatus());
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

    protected abstract void onRootImportFinished(ImportRequest request, Process process);

    public void setRequest(ImportRequest request) {
        this.request = request;
    }

    public void setPool(ImportPoolManagerBase pool) {
        this.pool = pool;
    }
}
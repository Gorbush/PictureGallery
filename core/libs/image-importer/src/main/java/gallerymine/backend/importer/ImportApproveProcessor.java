package gallerymine.backend.importer;

import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.ImportService;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

@Component
@Scope("prototype")
public class ImportApproveProcessor extends ImportProcessorBase {

    private static Logger log = LoggerFactory.getLogger(ImportApproveProcessor.class);

    public static final String KIND_APPROVE = "Approve";

    public static final ImportPoolManagerBase.StatusHolder STATUSES =
            ImportApproveRequestPoolManager.StatusHolder.define(APPROVING_AWAIT, APPROVING, APPROVED, APPROVAL_COMPLETE)
                    .processing(TO_APPROVE)
                    .abandoned();

    @Autowired
    private GenericFileAnalyser fileAnalyzer;

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @Autowired
    private SourceFilesMatcher sourceFilesMatcher;

    @Autowired
    private ImportService importService;

    public ImportApproveProcessor() {
        super(STATUSES, ProcessType.APPROVAL);
    }

    public void requestProcessing(ImportRequest request, Process process) throws ImportFailedException {
        log.warn("   approve processing start id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        ImportRequest.ImportStats stats = request.getStats(processType);
        ImportRequest.ImportStats statsEnum = request.getStats(ProcessType.MATCHING);
        stats.setFolders(statsEnum.getFolders());
        stats.setFiles(statsEnum.getFiles());

        stats.setAllFilesProcessed(stats.getFiles().get() == 0L);
        stats.setAllFoldersProcessed(stats.getFolders().get() == 0L);

        if (stats.getAllFilesProcessed() && stats.getAllFoldersProcessed()) {
            requestRepository.save(request);
            importService.checkSubsAndDone(request.getId(), null, processType, statusHolder.getProcessingDone());
        } else {
            request.setStatus(statusHolder.getInProcessing());
            requestRepository.save(request);
        }

        log.info("   approve processing done id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

}
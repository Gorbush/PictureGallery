package gallerymine.backend.importer;

import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public ImportApproveProcessor() {
        super(STATUSES, ProcessType.APPROVAL);
    }

    public void requestProcessing() throws ImportFailedException {
        log.info("   approve processing start id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        request = requestService.retrySave(request.getId(), request -> {
            ImportRequest.ImportStats stats = request.getStats(processType);
            ImportRequest.ImportStats statsMatch = request.getStats(ProcessType.MATCHING);

            stats.getFolders().set(statsMatch.getFolders().get());
            stats.getFiles().set(statsMatch.getFiles().get());

            stats.getDuplicates().set(statsMatch.getDuplicates().get());
            stats.getFailed().set(statsMatch.getFailed().get());
            stats.getSkipped().set(statsMatch.getSkipped().get());
            stats.getSimilar().set(statsMatch.getSimilar().get());

            stats.setAllFilesProcessed(stats.checkAllFilesProcessed());
            stats.setAllFoldersProcessed(stats.getFolders().get() == 0L);

            return request;
        });
        process.addNote("Incoming statistics:");
        importService.addImportStats(process, request);

        ImportRequest.ImportStats stats = request.getStats(processType);
        if (stats.getAllFilesProcessed() && stats.getAllFoldersProcessed()) {
            importService.checkSubsAndDone(request.getId(), null, processType, statusHolder.getProcessingDone());
        } else {
            requestService.updateStatus(request.getId(), statusHolder.getInProcessing());
        }

        log.info("   approve processing done id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

}
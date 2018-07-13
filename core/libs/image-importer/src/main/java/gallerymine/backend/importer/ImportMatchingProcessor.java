package gallerymine.backend.importer;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.UniSourceService;
import gallerymine.model.ImportSource;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.ProcessType;
import gallerymine.model.support.SourceMatchReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Iterator;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;
import static gallerymine.model.support.PictureGrade.IMPORT;

@Component
@Scope("prototype")
public class ImportMatchingProcessor extends ImportProcessorBase {

    private static Logger log = LoggerFactory.getLogger(ImportMatchingProcessor.class);

    public static final String KIND_MATCHING = "Matching";

    public static final ImportPoolManagerBase.StatusHolder STATUSES =
            ImportPoolManagerBase.StatusHolder.define(MATCHING_AWAIT, MATCHING, MATCHED, MATCHING_COMPLETE)
                    .processing(TO_MATCH)
                    .abandoned(MATCHING_AWAIT, MATCHING, MATCHED);

    @Autowired
    private ImportSourceRepository importSourceRepository;

    @Autowired
    private UniSourceService uniSourceService;

    @Autowired
    private SourceFilesMatcher sourceFilesMatcher;

    public ImportMatchingProcessor() {
        super(STATUSES, ProcessType.MATCHING);
    }

    public void requestProcessing() throws ImportFailedException {
        log.warn("   matching processing start id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

        if (!validateImportRequest(process, path))
            return;

        request = requestService.retrySave(request.getId(), request -> {
                    request.setStatus(statusHolder.getInProcessing());
                    ImportRequest.ImportStats stats = request.getStats(processType);
                    ImportRequest.ImportStats statsEnum = request.getStats(ProcessType.IMPORT);
                    stats.setFolders(statsEnum.getFolders());
                    stats.setFiles(statsEnum.getFilesDone());
                    return true;
                });
        updateMarker();

        log.info(" matching processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        try {
            int filesCount = 0;
            int filesSucceedCount = 0;
            int filesFailedCount = 0;
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.setStatus(InfoStatus.ANALYSING);
            criteria.setPopulatedNotBy(Sets.newHashSet(KIND_MATCHING));

            Iterator<ImportSource> importSources = sourceRepository.fetchCustomStream(criteria, ImportSource.class);

            while (importSources.hasNext()) {
                filesCount++;
                ImportSource infoImg = importSources.next();
                try {
                    SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(infoImg);

                    infoImg = uniSourceService.retrySave(infoImg.getId(), ImportSource.class, info -> {
                        info.setMatchReport(matchReport);
                        info.getPopulatedBy().add(KIND_MATCHING);
                        info.setStatus(InfoStatus.APPROVING);
                        return true;
                    });
                    filesSucceedCount++;
                } catch (Exception e) {
                    filesFailedCount++;
                    log.error("   matching processing failed: Failed processing info id={} path={}", infoImg.getId(), infoImg.getFileName());
                }
                log.info("   matching processing done {} or {} succeeded. id={} status={} path={}",
                        filesCount, filesSucceedCount,
                        request.getId(), request.getStatus(), request.getPath());
            }

            String info = String.format("Matching info gathered for id=%s files %d of %d. Failed:%d", request.getId(), filesSucceedCount, filesCount, filesCount-filesSucceedCount);
            log.info(" "+info);

            final int doneFilesCount = filesCount;
            final int doneFilesSucceedCount = filesSucceedCount;
            final int doneFilesFailedCount = filesFailedCount;

            request = requestService.retrySave(request.getId(), request -> {
                request.addNote(info);
                request.getStats(processType).getMovedToApprove().set(doneFilesSucceedCount);
                request.getStats(processType).getFilesDone().set(doneFilesCount);
                request.getStats(processType).getFailed().set(doneFilesFailedCount);
                request.setStatus(statusHolder.getProcessingDone());
                request.getStats(processType).setAllFilesProcessed(true);
                return true;
            });
            updateMarker();
        } catch (Exception e) {
            request = requestService.addError(request.getId(), "Matching info analysing failed");
            log.error("   matching processing failed: Matching info analysing failed for indexRequest id=%s {}. Reason: {}", path, e.getMessage());
        }
        log.warn("   matching processing done id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

}
package gallerymine.backend.importer;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.ImportService;
import gallerymine.model.ImportSource;
import gallerymine.model.Process;
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
    private GenericFileAnalyser fileAnalyzer;

    @Autowired
    private ImportSourceRepository importSourceRepository;

    @Autowired
    private SourceFilesMatcher sourceFilesMatcher;

    @Autowired
    private ImportService importService;

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    public ImportMatchingProcessor() {
        super(STATUSES, ProcessType.MATCHING);
    }

    public void requestProcessing(ImportRequest request, Process process) throws ImportFailedException {
        Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

        if (!validateImportRequest(process, path))
            return;

        request.setStatus(statusHolder.getInProcessing());
        ImportRequest.ImportStats stats = request.getStats(processType);
        ImportRequest.ImportStats statsEnum = request.getStats(ProcessType.IMPORT);
        stats.setFolders(statsEnum.getFolders());
        requestRepository.save(request);
        log.info(this.getClass().getSimpleName()+" matching processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        try {
            int filesCount = 0;
            int filesSucceedCount = 0;
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.setStatus(InfoStatus.ANALYSING);
            criteria.setPopulatedNotBy(Sets.newHashSet(KIND_MATCHING));

            Iterator<ImportSource> importSources = sourceRepository.fetchCustomStream(criteria, ImportSource.class);

            while (importSources.hasNext()) {
                filesCount++;
                ImportSource info = importSources.next();
                try {
                    SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(info);

                    info.setMatchReport(matchReport);
                    info.getPopulatedBy().add(KIND_MATCHING);
                    info.setStatus(InfoStatus.APPROVING);
                    importSourceRepository.saveByGrade(info);
                    filesSucceedCount++;
                    request.getStats(processType)
                            .incMovedToApprove()
                            .incFiles();
                } catch (Exception e) {
                    request.getStats(processType).incFailed();
                    log.error(this.getClass().getSimpleName()+" Failed processing info id=%s path=%s", info.getId(), info.getFileName());
                }
                requestRepository.save(request);
            }

            String info = request.addNote("Matching info gathered for id=%s files %d of %d. Failed:%d",
                    request.getId(), filesSucceedCount, filesCount, filesCount-filesSucceedCount);
            log.info(this.getClass().getSimpleName()+" "+info);

            request.setStatus(statusHolder.getProcessingDone());
            request.getStats(processType).setAllFilesProcessed(true);
            requestRepository.save(request);
        } catch (Exception e) {
            request.addError("Matching info analysing failed for indexRequest id=%s", request.getId());
            requestRepository.save(request);
            log.error(this.getClass().getSimpleName()+" Matching info analysing failed for indexRequest id=%s {}. Reason: {}", path, e.getMessage());
        }
    }

    @Override
    protected void onRootImportFinished(ImportRequest request, Process process) {
        uniSourceRepository.updateAllRequestsToNextProcess(process.getId(), null, MATCHED, MATCHING_COMPLETE);
        importService.checkIfApproveNeeded(request, process);
    }

}
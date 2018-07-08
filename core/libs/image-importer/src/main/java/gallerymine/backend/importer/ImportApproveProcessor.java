package gallerymine.backend.importer;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.exceptions.ImportFailedException;
import gallerymine.backend.helpers.analyzer.GenericFileAnalyser;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.pool.ImportApproveRequestPoolManager;
import gallerymine.backend.pool.ImportPoolManagerBase;
import gallerymine.backend.services.ImportService;
import gallerymine.model.ImportSource;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.SourceCriteria;
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
public class ImportApproveProcessor extends ImportProcessorBase {

    private static Logger log = LoggerFactory.getLogger(ImportApproveProcessor.class);

    public static final String KIND_APPROVE = "Approve";

    public static final ImportPoolManagerBase.StatusHolder STATUSES =
            ImportApproveRequestPoolManager.StatusHolder.define(MATCHING_AWAIT, MATCHING, MATCHED, MATCHING_COMPLETE)
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

    public ImportApproveProcessor() {
        super(STATUSES, ProcessType.APPROVAL);
    }

    public void requestProcessing(ImportRequest request, Process process) throws ImportFailedException {
        Path path = appConfig.getImportRootFolderPath().resolve(request.getPath());

        if (!validateImportRequest(process, path))
            return;

        request.setStatus(statusHolder.getInProcessing());
        requestRepository.save(request);
        log.info(this.getClass().getSimpleName()+" approve processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        try {
            int filesCount = 0;
            int filesSucceedCount = 0;
            SourceCriteria criteria = new SourceCriteria();
            criteria.setRequestId(request.getId());
            criteria.setPopulatedNotBy(Sets.newHashSet(KIND_APPROVE));

            Iterator<ImportSource> importSources = sourceRepository.fetchCustomStream(criteria, ImportSource.class);
                while (importSources.hasNext()) {
                    filesCount++;
                    ImportSource info = importSources.next();
                    try {
                        SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(info);

                        info.setMatchReport(matchReport);
                        info.getPopulatedBy().add(KIND_APPROVE);
                        importSourceRepository.save(info);
                        filesSucceedCount++;
                    } catch (Exception e) {
                        log.error(this.getClass().getSimpleName()+" Failed processing info id=%s path=%s", info.getId(), info.getFileName());
                    }
                }

            String info = request.addNote("Approve info gathered for id=%s files %d of %d. Failed:%d",
                    request.getId(), filesSucceedCount, filesCount, filesCount-filesSucceedCount);
            log.info(this.getClass().getSimpleName()+" "+info);

            request.setStatus(statusHolder.getProcessingDone());
            requestRepository.save(request);
        } catch (Exception e) {
            request.addError("approve info analysing failed for indexRequest id=%s", request.getId());
            requestRepository.save(request);
            log.error(this.getClass().getSimpleName()+" approve info analysing failed for indexRequest id=%s {}. Reason: {}", path, e.getMessage());
        }
    }

    @Override
    protected void onRootImportFinished(ImportRequest request, Process process) {
        request.setStatus(statusHolder.getFinished());
        requestRepository.save(request);
//        importService.checkIfApproveNeeded(request, process);
    }

}
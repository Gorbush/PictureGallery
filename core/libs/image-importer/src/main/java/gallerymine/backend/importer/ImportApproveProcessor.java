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
            ImportApproveRequestPoolManager.StatusHolder.define(APPROVING_AWAIT, null, APPROVED, APPROVAL_COMPLETE)
                    .processing(TO_APPROVE)
                    .abandoned();

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
        log.warn(this.getClass().getSimpleName()+" approve processing id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

    @Override
    protected void onRootImportFinished(ImportRequest request, Process process) {
//        importService.checkIfApproveNeeded(request, process);
    }

}
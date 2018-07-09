package gallerymine.backend.pool;

import gallerymine.backend.importer.ImportApproveProcessor;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pool manager for ImportRequests and processors
 * Created by sergii_puliaiev on 6/14/18.
 */
@Component
public class ImportApproveRequestPoolManager extends ImportPoolManagerBase {

    private static Logger log = LoggerFactory.getLogger(ImportApproveRequestPoolManager.class);

    public ImportApproveRequestPoolManager() {
        super(ImportApproveProcessor.STATUSES, "ImportApproveRequest", ImportApproveProcessor.class, ProcessType.APPROVAL);
    }

    @Override
    public void checkForAwaitingRequests() {
//        super.checkForAwaitingRequests();
    }

    @Override
    public void checkForAbandonedRequests() {
//        super.checkForAbandonedRequests();
    }
}

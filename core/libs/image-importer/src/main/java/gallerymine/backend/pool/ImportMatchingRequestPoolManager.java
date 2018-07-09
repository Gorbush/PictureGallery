package gallerymine.backend.pool;

import gallerymine.backend.importer.ImportMatchingProcessor;
import gallerymine.model.support.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pool manager for ImportRequests and processors
 * Created by sergii_puliaiev on 6/14/18.
 */
@Component
public class ImportMatchingRequestPoolManager extends ImportPoolManagerBase {

    private static Logger log = LoggerFactory.getLogger(ImportMatchingRequestPoolManager.class);

    public ImportMatchingRequestPoolManager() {
        super(ImportMatchingProcessor.STATUSES, "ImportMatchingRequest", ImportMatchingProcessor.class, ProcessType.MATCHING);
    }
}

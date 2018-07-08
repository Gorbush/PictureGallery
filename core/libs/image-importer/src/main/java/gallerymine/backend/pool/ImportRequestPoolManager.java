package gallerymine.backend.pool;

import gallerymine.backend.importer.ImportApproveProcessor;
import gallerymine.backend.importer.ImportProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pool manager for ImportRequests and processors
 * Created by sergii_puliaiev on 6/14/18.
 */
@Component
public class ImportRequestPoolManager extends ImportPoolManagerBase {

    private static Logger log = LoggerFactory.getLogger(ImportRequestPoolManager.class);

    public ImportRequestPoolManager() {
        super(ImportProcessor.STATUSES, "ImportRequest", ImportProcessor.class);
    }
}

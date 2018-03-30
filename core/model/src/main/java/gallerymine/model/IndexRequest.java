package gallerymine.model;

import lombok.Data;
import org.joda.time.DateTime;

import static gallerymine.model.IndexRequest.IndexStatus.DONE;
import static gallerymine.model.IndexRequest.IndexStatus.FAILED;
import static gallerymine.model.IndexRequest.IndexStatus.FOUND;
import static gallerymine.model.IndexRequest.IndexStatus.FOUND;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 6/11/17.
 */
@Data
public class IndexRequest  {

    public enum IndexStatus {
        FOUND,
        AWAITING,
        ENUMERATING,
        ENUMERATED,
        FILES_PROCESSING,
        RESTART,
        SUB,
        FAILED,
        DONE
    }

    private String id;

    private String parent;

    private String path;

    private IndexStatus status = FOUND;

    private Boolean allFilesProcessed = false;
    private Boolean allFoldersProcessed = false;
    private String error;

//    @CreatedDate
    private DateTime created;
//    @LastModifiedDate
    private DateTime updated;

    public IndexRequest() {
    }

    public IndexRequest(String path) {
        this.path = path;
    }

    public boolean isProcessable() {
        return status != null && (
                        IndexStatus.AWAITING.equals(status) ||
                        FOUND.equals(status) ||
                        IndexStatus.FAILED.equals(status) ||
                        IndexStatus.RESTART.equals(status)
        );
    }

    public boolean isComplete() {
        return DONE.equals(status) || FAILED.equals(status);
    }

}

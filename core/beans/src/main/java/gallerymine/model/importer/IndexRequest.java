package gallerymine.model.importer;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import static gallerymine.model.importer.IndexRequest.IndexStatus.*;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document(collection = "indexRequest")
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

    @Id
    private String id;

    private String parent;

    @Indexed(unique = true)
    private String path;

    private IndexStatus status = FOUND;

    private Boolean allFilesProcessed = false;
    private Boolean allFoldersProcessed = false;
    private String error;
    private Integer filesCount;
    private Integer filesIgnoredCount;
    private Integer foldersCount;

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

    public IndexRequest() {
    }

    public IndexRequest(String path) {
        this.path = path;
    }

    public boolean isProcessable() {
        return status != null && (
                        AWAITING.equals(status) ||
                        FOUND.equals(status) ||
                        FAILED.equals(status) ||
                        RESTART.equals(status)
        );
    }

    public boolean isComplete() {
        return DONE.equals(status) || FAILED.equals(status);
    }

    public void addError(String s) {
        if (error == null) {
            error = s;
        } else {
            error += "\n"+s;
        }
    }

}

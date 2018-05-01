package gallerymine.model.importer;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.file.Path;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 6/11/18.
 */
@Document(collection = "importRequest")
@Data
public class ImportRequest {

    public enum ImportStatus {
        START,
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
    private String rootId;

    @Indexed(unique = true)
    private String path;

    private String originalPath;

    private ImportStatus status = ImportStatus.START;

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

    public ImportRequest() {
    }

    public ImportRequest(Path originalPath) {
        this(originalPath.toFile().getAbsolutePath());
    }

    public ImportRequest(String path) {
        this.originalPath = path;
    }

    public boolean isProcessable() {
        return status != null && (
                        AWAITING.equals(status) ||
                        START.equals(status) ||
                        FAILED.equals(status) ||
                        RESTART.equals(status)
        );
    }

    public boolean isComplete() {
        return DONE.equals(status) || FAILED.equals(status);
    }

    public ImportRequest addError(String s) {
        if (error == null) {
            error = s;
        } else {
            error += "\n"+s;
        }
        return this;
    }

    public ImportRequest markFailed() {
        status = FAILED;
        return this;
    }

}

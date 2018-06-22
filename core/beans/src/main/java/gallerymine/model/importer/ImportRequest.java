package gallerymine.model.importer;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 6/11/18.
 */
@Document(collection = "importRequest")
@Data
public class ImportRequest {

    public enum ImportStatus {
        PREREARING(false),
        START(false),
        AWAITING(false),
        ENUMERATING(false),
        ENUMERATED(false),
        FILES_PROCESSING(false),
        RESTART(false),
        SUB(false),
        FAILED(true),
        DONE(true);

        private boolean aFinal;

        private ImportStatus(boolean aFinal) {
            this.aFinal = aFinal;
        }

        public boolean isFinal() {
            return aFinal;
        }
    }

    @Data
    public class ImportStats {
        AtomicLong processed = new AtomicLong();
        AtomicLong movedToApprove = new AtomicLong();
        AtomicLong similar = new AtomicLong();
        AtomicLong duplicates = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        AtomicLong folders = new AtomicLong();
        AtomicLong files = new AtomicLong();
        AtomicLong foldersDone = new AtomicLong();

        public ImportStats append(ImportStats subStats) {
            processed.addAndGet(subStats.processed.get());
            movedToApprove.addAndGet(subStats.movedToApprove.get());
            similar.addAndGet(subStats.similar.get());
            duplicates.addAndGet(subStats.duplicates.get());
            failed.addAndGet(subStats.failed.get());

            folders.addAndGet(subStats.folders.get());
            foldersDone.addAndGet(subStats.foldersDone.get());
            files.addAndGet(subStats.files.get());
            return this;
        }

        public ImportStats incProcessed() {
            processed.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incFailed() {
            failed.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incMovedToApprove() {
            movedToApprove.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incSimilar() {
            similar.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incDuplicates() {
            duplicates.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incFolders() {
            folders.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incFoldersDone() {
            foldersDone.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
        public ImportStats incFiles() {
            files.incrementAndGet();
            setUpdated(DateTime.now());
            return this;
        }
    }

    @Id
    private String id;

    private String parent;
    private String rootId;

    /** Import process current folder */
    @Indexed(unique = true)
    private String path;

    /** Import process root folder */
    private String rootPath;

    private String originalPath;

    private ImportStatus status = ImportStatus.START;

    private Boolean allFilesProcessed = false;
    private Boolean allFoldersProcessed = false;
    private String error;
    private Integer filesCount;
    private Integer filesIgnoredCount;
    private Integer foldersCount;

    @Indexed
    private String indexProcessId;

    private ImportStats stats = new ImportStats();
    private ImportStats subStats = new ImportStats();

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

    public ImportRequest addError(String s, Object... params) {
        if (params != null && params.length > 0) {
            s = String.format(s, params);
        }
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

    public void appendSubStats(ImportStats subStats) {
        this.subStats.append(subStats);
        setUpdated(DateTime.now());
    }

    public ImportStats getTotalStats() {
        return new ImportStats()
                .append(subStats)
                .append(stats);
    }
}

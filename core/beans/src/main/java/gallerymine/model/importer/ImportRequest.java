package gallerymine.model.importer;

import com.google.common.collect.ImmutableSet;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 6/11/18.
 */
@Document(collection = "importRequest")
@Data
public class ImportRequest {

    public enum ImportStatus {
        /* Name (isFinal, isInProgress) */

        INIT(false, true),
        START(false, false),
        AWAITING(false, true),

        /** File analysing statuses start */
        TO_ENUMERATE(false, false),
        ENUMERATING_AWAIT(false, true),
        ENUMERATING(false, true),
        ENUMERATED(true, false),
        ENUMERATION_COMPLETE(true, false),
        /** File analysing statuses end */

        /** Repository matching statuses start */
        TO_MATCH(false, false),
        MATCHING_AWAIT(false, true),
        MATCHING(false, true),
        MATCHED(true, false),
        MATCHING_COMPLETE(true, false),
        /** Repository matching statuses end */

        /** User approval statuses start */
        TO_APPROVE(false, false),
        APPROVING_AWAIT(false, true),
        APPROVED(true, true),
        APPROVAL_COMPLETE(false, false),
        /** User approval statuses end */

        RESTART(false, false),
        FAILED(true, false),
        ABANDONED(true, false),
        DONE(true, false);

        private boolean aFinal;
        private boolean inProgress;

        /** Status which might mean the import is abandoned */
        static Collection<ImportStatus> inProgressStatuses =
                ImmutableSet.of(INIT, AWAITING, ENUMERATING, ENUMERATED);

        ImportStatus(boolean aFinal, boolean inProgress) {
            this.aFinal = aFinal;
            this.inProgress = inProgress;
        }

        public boolean isFinal() {
            return aFinal;
        }

        public boolean isInProgress() {
            return inProgress;
        }

        public static Collection<ImportStatus> getInProgress() {
            return inProgressStatuses;
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
        public ImportStats incFolders(long count) {
            folders.addAndGet(count);
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
    @Indexed
    private String path;

    @Indexed
    private String name;

    @Indexed
    private String nameL;

    /** Import process root folder */
    private String rootPath;

    private String originalPath;

    private ImportStatus status = ImportStatus.START;

    private Boolean allFilesProcessed = false;
    private Boolean allFoldersProcessed = false;
    private List<String> errors = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    private Integer filesCount;
    private Integer filesIgnoredCount;
    private Integer foldersCount;

    @Indexed
    private Set<String> indexProcessIds = new HashSet<>();

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
                        TO_ENUMERATE.equals(status) ||
                        AWAITING.equals(status) ||
                        START.equals(status) ||
                        FAILED.equals(status) ||
                        RESTART.equals(status)
        );
    }

    public boolean isComplete() {
        return DONE.equals(status) || FAILED.equals(status);
    }

    public String addError(String error, Object... params) {
        if (params!= null && params.length > 0) {
            error = String.format(error, params);
        }
        errors.add(error);
        return error;
    }

    public String addNote(String note, Object... params) {
        if (params!= null && params.length > 0) {
            note = String.format(note, params);
        }
        notes.add(note);
        return note;
    }

    public String notesText() {
        return notes.stream().collect(Collectors.joining("\n"));
    }

    public String errorsText() {
        return errors.stream().collect(Collectors.joining("\n"));
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

    public void setName(String name) {
        this.name = name;
        nameL = name == null ? null : name.toLowerCase().replaceAll("^[_$/\\\\]*", "");
    }

    public void addIndexProcessId(String indexProcessId) {
        indexProcessIds.add(indexProcessId);
    }

}

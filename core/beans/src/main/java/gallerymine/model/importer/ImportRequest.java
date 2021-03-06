package gallerymine.model.importer;

import com.google.common.collect.ImmutableSet;
import gallerymine.model.support.ProcessType;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
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

        INIT(false, true, "init"),
        START(false, false, "start"),
        AWAITING(false, true, "await"),

        /** File analysing statuses start */
        TO_ENUMERATE(false, false, "to_enum"),
        ENUMERATING_AWAIT(false, true, "enum_awt"),
        ENUMERATING(false, true, "enuming"),
        ENUMERATED(true, false, "enumed"),
        ENUMERATION_COMPLETE(true, false, "enum_don"),
        /** File analysing statuses end */

        /** Repository matching statuses start */
        TO_MATCH(false, false, "to_mtch"),
        MATCHING_AWAIT(false, true, "mtch_awt"),
        MATCHING(false, true, "mtching"),
        MATCHED(true, false, "mtched"),
        MATCHING_COMPLETE(true, false, "mtch_don"),
        /** Repository matching statuses end */

        /** User approval statuses start */
        TO_APPROVE(false, false,"to_aprv"),
        APPROVING_AWAIT(false, true,"aprv_awt"),
        APPROVING(false, true,"aprving"),
        APPROVED(true, false,"aprved"),
        APPROVAL_COMPLETE(true, false,"aprv_cpl"),
        /** User approval statuses end */

        RESTART(false, false,"restart"),
        FAILED(true, false,"failed"),
        ABANDONED(true, false,"abnd"),
        DONE(true, false,"done");

        private boolean aFinal;
        private boolean inProgress;
        private String label;

        /** Status which might mean the import is abandoned */
        static Collection<ImportStatus> inProgressStatuses =
                ImmutableSet.of(INIT, AWAITING, ENUMERATING, ENUMERATED);

        ImportStatus(boolean aFinal, boolean inProgress, String label) {
            this.aFinal = aFinal;
            this.inProgress = inProgress;
            this.label = label;
        }

        public boolean isFinal() {
            return aFinal;
        }

        public boolean isInProgress() {
            return inProgress;
        }

        public String getLabel() {
            return label;
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
        AtomicLong skipped = new AtomicLong();

        AtomicLong folders = new AtomicLong();
        AtomicLong files = new AtomicLong();
        AtomicLong foldersDone = new AtomicLong();
        AtomicLong filesDone = new AtomicLong();

        private Boolean allFilesProcessed = false;
        private Boolean allFoldersProcessed = false;

        public boolean checkAllFilesProcessed() {
            long countDone = getFilesProcessed();
            return files.get() <= countDone;
        }

        public long getFilesProcessed() {
            return (failed.get() + skipped.get() + movedToApprove.get() + duplicates.get());
        }

        public ImportStats append(ImportStats subStats) {
            processed.addAndGet(subStats.processed.get());
            movedToApprove.addAndGet(subStats.movedToApprove.get());
            similar.addAndGet(subStats.similar.get());
            duplicates.addAndGet(subStats.duplicates.get());
            failed.addAndGet(subStats.failed.get());
            skipped.addAndGet(subStats.skipped.get());

            folders.addAndGet(subStats.folders.get());
            foldersDone.addAndGet(subStats.foldersDone.get());
            files.addAndGet(subStats.files.get());
            filesDone.addAndGet(subStats.filesDone.get());
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
        public ImportStats incSkipped() {
            skipped.incrementAndGet();
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
        public ImportStats incFilesDone() {
            filesDone.incrementAndGet();
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

    private List<String> errors = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
    private Integer filesCount;
    private Integer filesIgnoredCount;
    private Integer foldersCount;

    @Indexed
    private Set<String> indexProcessIds = new HashSet<>();

    private Map<ProcessType, ImportStats> stats = new HashMap<>();
    private Map<ProcessType, ImportStats> subStats = new HashMap<>();
    private Map<ProcessType, Set<String>> statsAppended = new HashMap<>();

    private String activeProcessId;
    private ProcessType activeProcessType = null;

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

    @Version
    private Long version;

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

    /** Appends subordinate ImportRequest stats to the parent ImportRequest
     * if it was not yet added. Returns false if already added before.*/
    public boolean appendSubStats(ProcessType processType, ImportRequest subStats) {
        Set<String> applied = statsAppended.computeIfAbsent(processType, p -> new HashSet<>());
        if (applied.contains(subStats.id)) {
            return false;
        }
        applied.add(subStats.id);
        getSubStats(processType).append(subStats.getTotalStats(processType));
        setUpdated(DateTime.now());
        return true;
    }

    public ImportStats getTotalStats(ProcessType processType) {
        return new ImportStats()
                .append(getSubStats(processType))
                .append(getStats(processType));
    }

    public ImportStats getTotalStats() {
        if (activeProcessType != null) {
            return new ImportStats()
                    .append(getSubStats(activeProcessType))
                    .append(getStats(activeProcessType));
        } else {
            return null;
        }
    }

    public void setName(String name) {
        this.name = name;
        nameL = name == null ? null : name.toLowerCase().replaceAll("^[_$/\\\\]*", "");
    }

    public void addIndexProcessId(String indexProcessId) {
        indexProcessIds.add(indexProcessId);
    }

    public ImportStats getStats(ProcessType processType) {
        return stats.computeIfAbsent(processType, k -> new ImportStats());
    }

    public ImportStats getSubStats(ProcessType processType) {
        return subStats.computeIfAbsent(processType, k -> new ImportStats());
    }

    public String marker() {
        StringBuilder res = new StringBuilder();
        int CUT_PART = 5;
        if (activeProcessId != null && activeProcessId.length() > CUT_PART) {
            res.append("IP");
            int len = activeProcessId.length();
            res.append(activeProcessId, len-CUT_PART, len);
        }
        res.append(":");
        if (rootId != null && rootId.length() > CUT_PART) {
            int len = rootId.length();
            res.append("IR");
            res.append(rootId, len-CUT_PART, len);
        }
        res.append(":");
        if (id != null && id.length() > CUT_PART) {
            int len = id.length();
            res.append("IR");
            res.append(id, len-CUT_PART, len);
        }
        res.append("[");
        res.append(status.getLabel());
        res.append("]");
        return res.toString();
    }
}

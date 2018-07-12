package gallerymine.model;

import com.querydsl.core.annotations.QueryEntity;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Document
@QueryEntity
@Data
public class Process {
    @Id
    private String id;

    private DateTime started;
    private DateTime finished;

    private String name;
    private ProcessType type;

    /** ID of parent, for example - matching process after import */
    private String parentProcessId;

    private ProcessStatus status;

    private List<String> errors = new ArrayList<>();

    private List<String> notes = new ArrayList<>();

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

    @Version
    private long version = 0;


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

    public void setStatus(ProcessStatus status) {
        if (status != null && status.isFinalStatus() && finished == null) {
            finished = DateTime.now();
        }
        this.status = status;
    }

    public String notesText() {
        return notes.stream().collect(Collectors.joining("\n"));
    }

    public String errorsText() {
        return errors.stream().collect(Collectors.joining("\n"));
    }

    public boolean isFinished() {
        return status.isFinalStatus();
    }
}

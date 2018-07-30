package gallerymine.model.support;

import lombok.Data;
import org.joda.time.DateTime;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class ImageInformation {

    public int orientation;
    public int width;
    public int height;
    public Date originalDate;
    public File file;
    public File thumbFile;
    public boolean extractThumb;
    public int thumbWidth;
    public int thumbHeight;
    public Double latitude;
    public Double longitude;
    public SortedSet<Timestamp> timestamps = new TreeSet<>();
    public String device;
    public String deviceMaker;
    public String flash;
    public boolean facesDetected;
    private List<String> errors = new ArrayList<>();

    private List<String> notes = new ArrayList<>();

    public String toString() {
        return String.format("%dx%d,%d", this.width, this.height, this.orientation);
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

    public void addStamp(Timestamp timestamp) {
        if (timestamp != null) {
            timestamps.add(timestamp);
        }
    }

    public boolean hasTimeStamp(TimestampKind kind) {
        if (kind == null) {
            return false;
        }
        for (Timestamp timestamp : timestamps) {
            if (kind.equals(timestamp.getKind())) {
                return true;
            }
        }
        return false;
    }
}

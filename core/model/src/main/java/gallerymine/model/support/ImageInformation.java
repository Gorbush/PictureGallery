package gallerymine.model.support;

import lombok.Data;

import java.io.File;
import java.util.*;

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

    public String toString() {
        return String.format("%dx%d,%d", this.width, this.height, this.orientation);
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

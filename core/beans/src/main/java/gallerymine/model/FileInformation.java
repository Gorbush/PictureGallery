package gallerymine.model;

import gallerymine.model.support.Timestamp;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document
@Data
public class FileInformation implements Comparable<FileInformation> {

    @Id
    private String id;

    @Indexed
    private String filePath;
    @Indexed
    private String fileName;
    private String source;
    private boolean filled;
    private boolean exists;
    private SortedSet<Timestamp> timestamps = new TreeSet<>();
    @Indexed
    private long size;
    private long width;
    private long height;
    private int orientation;
    private GeoJsonPoint geoLocation;
    private String error;
    @Indexed
    private String thumbPath;
    @Indexed
    private DateTime timestamp;
    private String device;

    private boolean assignedToPicture = false;

    @CreatedDate
    private DateTime indexed;
    @LastModifiedDate
    private DateTime updated;

    public void updateTimestamp() {
        if (CollectionUtils.isEmpty(timestamps)) {
            this.timestamp = null;
        } else {
            this.timestamp = timestamps.first().getStamp();
        }
    }

    public void addStamps(SortedSet<Timestamp> timestamps) {
        this.timestamps.addAll(timestamps);
    }

    public boolean hasThumb() {
        return StringUtils.isNotBlank(thumbPath);
    }

    public String getFullFilePath() {
        return filePath+'/'+fileName;
    }

    public void copyFrom(Source sourceToMatch) {
        filePath = sourceToMatch.getFilePath();
        fileName = sourceToMatch.getFileName();
        source = sourceToMatch.getSource();
        filled = sourceToMatch.isFilled();
        exists = sourceToMatch.isExists();
        timestamps = new TreeSet<>();
        timestamps.addAll(sourceToMatch.getTimestamps());

        size = sourceToMatch.getSize();
        width = sourceToMatch.getWidth();
        height = sourceToMatch.getHeight();
        orientation = sourceToMatch.getOrientation();

        geoLocation = sourceToMatch.getGeoLocation();
        error = sourceToMatch.getError();
        thumbPath = sourceToMatch.getThumbPath();
        timestamp = sourceToMatch.getTimestamp();
        device = sourceToMatch.getDevice();

        assignedToPicture = sourceToMatch.isAssignedToPicture();
    }

    @Override
    public int compareTo(FileInformation o) {
        return StringUtils.compare(o.id, id, true);
    }
}

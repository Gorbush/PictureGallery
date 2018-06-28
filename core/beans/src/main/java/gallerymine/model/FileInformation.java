package gallerymine.model;

import gallerymine.model.support.InfoStatus;
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

import java.util.*;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document
@Data
public class FileInformation implements Comparable<FileInformation> {

    @Id
    private String id;

    @Indexed
    private String storage;
    @Indexed
    private String filePath;
    @Indexed
    private String fileName;
    /** This should be a path to the original root folder */
    private String rootPath;
    private String source;
    private boolean filled;
    private boolean exists;
    private SortedSet<Timestamp> timestamps = new TreeSet<>();
    @Indexed
    private long size;

    @Indexed
    private InfoStatus status = InfoStatus.ANALYSING;

    /** Kind of information populated into record */
    private Set<String> populatedBy = new HashSet<>();

    private String error;
    @Indexed
    private String thumbPath;
    @Indexed
    private DateTime timestamp;

    @Indexed
    String importRequestRootId;

    @Indexed
    String importRequestId;

    @Indexed
    private Set<String> indexProcessIds = new HashSet<>();

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

    public void addStamps(Collection<Timestamp> timestamps) {
        this.timestamps.addAll(timestamps);
        updateTimestamp();
    }

    public void addStamp(Timestamp timestamp) {
        this.timestamps.add(timestamp);
        updateTimestamp();
    }

    public boolean hasThumb() {
        return StringUtils.isNotBlank(thumbPath);
    }

    public String getFullFilePath() {
        if (StringUtils.isNotBlank(filePath)) {
            return filePath + '/' + fileName;
        } else {
            return fileName;
        }
    }

    public String getLocation() {
        return
            (StringUtils.isNotBlank(storage) ? (storage+":") : "") +
            (StringUtils.isNotBlank(filePath) ? (filePath + '/') : "") +
            fileName;
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

        error = sourceToMatch.getError();
        thumbPath = sourceToMatch.getThumbPath();
        timestamp = sourceToMatch.getTimestamp();
    }

    @Override
    public int compareTo(FileInformation o) {
        return StringUtils.compare(o.id, id, true);
    }

    public void addError(String message, Object... params) {
        if (params != null && params.length > 0) {
            message = String.format(message, params);
        }
        if (this.error == null) {
            this.error = message;
        } else {
            this.error += "\n" + message;
        }
    }

    public boolean isFailed() {
        return StringUtils.isNotBlank(error);
    }

    public void addIndexProcessId(String indexProcessId) {
        indexProcessIds.add(indexProcessId);
    }
}

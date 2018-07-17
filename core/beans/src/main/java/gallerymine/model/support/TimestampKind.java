package gallerymine.model.support;

import org.joda.time.DateTime;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
public enum TimestampKind {
    TS_UNKNOWN("UNKNOWN", 0),
    TS_FILE_CREATE("FILE_CREATE", 100),
    TS_FILE_MODIFY("FILE_MODIFY", 200),
    TS_FILE_EXIF_MODIFY("EXIF_MOD", 290),
    TS_FILE_EXIF("EXIF", 300),
    TS_FILE_EXIF_ORIGINAL("EXIF_ORG", 400),
    TS_FILE_NAME("FILE_NAME", 500),
    TS_FOLDER_NAME("FOLDER_NAME", 700),
    TS_GPS("GPS", 800),
    TS_MANUAL("MANUAL", 1000);

    private String kind;
    private int priority;

    TimestampKind(String kind, int priority) {
        this.kind = kind;
        this.priority = priority;
    }

    public String getKind() {
        return kind;
    }

    public int getPriority() {
        return priority;
    }

    public Timestamp create(DateTime dt) {
        if (dt == null) {
            return null;
        }
        return new Timestamp(this, dt, priority);
    }

    public Timestamp create(Object dt) {
        if (dt == null) {
            return null;
        }
        return new Timestamp(this, new DateTime(dt), priority);
    }

}

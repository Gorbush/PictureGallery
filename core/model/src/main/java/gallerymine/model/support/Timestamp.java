package gallerymine.model.support;

import lombok.Data;
import org.joda.time.DateTime;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Data
public class Timestamp implements Comparable<Timestamp> {

    TimestampKind kind;
    DateTime stamp;
    int priority;

    public Timestamp() {
    }

    public Timestamp(TimestampKind kind, DateTime stamp, int priority) {
        this.kind = kind;
        this.stamp = stamp;
        this.priority = priority;
    }
    
    @Override
    public int compareTo(Timestamp o) {
        int result = Integer.compare(o.priority, priority);

        if (result == 0) {
            result = Integer.compare(o.kind.ordinal(), kind.ordinal());
            if (result == 0) {
                result = Long.compare(o.getStamp().getMillis(), getStamp().getMillis());
            }
        }
        return result;
    }

//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        Timestamp timestamp = (Timestamp) o;
//
//        return kind == timestamp.kind;
//    }
//
//    @Override
//    public int hashCode() {
//        return kind != null ? kind.hashCode() : 0;
//    }

    @Override
    public String toString() {
        return "Timestamp("+priority+") "+kind+"="+stamp.toString("YYYY-MM-dd HH:mm:ss");
    }
}

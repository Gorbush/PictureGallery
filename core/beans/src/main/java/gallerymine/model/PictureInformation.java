package gallerymine.model;

import gallerymine.model.support.Timestamp;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
@EqualsAndHashCode(callSuper = true)
@Document
@Data
public class PictureInformation extends FileInformation {

    private long width;
    private long height;
    private int orientation;
    private GeoJsonPoint geoLocation;

    private String device;

    private boolean assignedToPicture = false;

    public void copyFrom(Source sourceToMatch) {
        super.copyFrom(sourceToMatch);

        width = sourceToMatch.getWidth();
        height = sourceToMatch.getHeight();
        orientation = sourceToMatch.getOrientation();

        geoLocation = sourceToMatch.getGeoLocation();

        device = sourceToMatch.getDevice();

        assignedToPicture = sourceToMatch.isAssignedToPicture();
    }

}

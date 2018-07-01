package gallerymine.model;

import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.SourceRef;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@EqualsAndHashCode(callSuper = true)
@Document
@Data
@ToString(callSuper = true)
public class PictureInformation extends FileInformation {

    private long width;
    private long height;
    private int orientation;
    private GeoJsonPoint geoLocation;

    private PictureGrade grade = PictureGrade.IMPORT;

    private String device;

    private boolean assignedToPicture = false;

    private Set<SourceRef> sources = new HashSet<>();

    public void addSource(String id, PictureGrade grade) {
        sources.add(new SourceRef(id, grade));
    }

    public <FI extends PictureInformation> void copyFrom(FI sourceToMatch) {
        super.copyFrom(sourceToMatch);

        width = sourceToMatch.getWidth();
        height = sourceToMatch.getHeight();
        orientation = sourceToMatch.getOrientation();

        geoLocation = sourceToMatch.getGeoLocation();

        device = sourceToMatch.getDevice();

        grade = sourceToMatch.getGrade();

        device = sourceToMatch.getDevice();

        assignedToPicture = sourceToMatch.isAssignedToPicture();

        sources = new HashSet<>();
        sources.addAll(sourceToMatch.getSources());
    }

}

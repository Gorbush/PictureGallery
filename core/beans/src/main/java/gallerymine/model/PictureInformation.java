package gallerymine.model;

import gallerymine.model.support.PictureGrade;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

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

    private Set<String> mappedPictures = new HashSet<>();
    private Set<String> mappedImports = new HashSet<>();

    private Long durationInSeconds = null;

    public void addPicture(String sourceId) {
        mappedPictures.add(sourceId);
    }

    public void addImport(String sourceId) {
        mappedImports.add(sourceId);
    }

    public String getPictureId() {
        return mappedPictures.stream().findFirst().orElse(null);
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

        mappedPictures = new HashSet<>();
        mappedPictures.addAll(sourceToMatch.getMappedPictures());

        mappedImports = new HashSet<>();
        mappedImports.addAll(sourceToMatch.getMappedImports());
    }

}

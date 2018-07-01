package gallerymine.model;

import gallerymine.model.support.PictureAccessKind;
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.PlaceRef;
import gallerymine.model.support.SourceRef;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Document(collection = "picture")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Picture extends PictureInformation {

    private String label;

    private Set<String> tags = new HashSet<>();

    private PictureAccessKind accessKind = PictureAccessKind.UNSET;

    private Set<String> sharedWith = new HashSet<>();

    private PlaceRef place;

    public <FI extends Picture> void copyFrom(FI sourceToMatch) {
        super.copyFrom(sourceToMatch);

        label = sourceToMatch.getLabel();

        tags = new HashSet<>();
        tags.addAll(sourceToMatch.getTags());

        accessKind = sourceToMatch.getAccessKind();

        sharedWith = new HashSet<>();
        sharedWith.addAll(sourceToMatch.getSharedWith());

        place = sourceToMatch.getPlace();
    }

}

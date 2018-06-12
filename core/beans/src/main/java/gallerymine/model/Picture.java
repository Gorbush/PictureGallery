package gallerymine.model;

import gallerymine.model.support.PictureAccessKind;
import gallerymine.model.support.PlaceRef;
import gallerymine.model.support.SourceRef;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;

@Document(collection = "picture")
@Data
@EqualsAndHashCode(callSuper = true)
public class Picture extends PictureInformation {

    private String label;

    private Set<String> tags = new HashSet<>();

    private Set<SourceRef> sources = new HashSet<>();

    private PictureAccessKind accessKind = PictureAccessKind.UNSET;

    private Set<String> sharedWith = new HashSet<>();

    private PlaceRef place;

}

package gallerymine.model;

import gallerymine.model.support.SourceKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by sergii_puliaiev on 6/11/18.
 */
@Document(collection = "importSource")
@Data
@EqualsAndHashCode(callSuper = true)
public class ImportSource extends PictureInformation {

    SourceKind kind = SourceKind.UNSET;

}

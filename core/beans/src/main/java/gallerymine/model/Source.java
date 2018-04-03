package gallerymine.model;

import gallerymine.model.support.SourceKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document(collection = "source")
@Data
@EqualsAndHashCode(callSuper = true)
public class Source extends FileInformation {

    SourceKind kind = SourceKind.UNSET;

}

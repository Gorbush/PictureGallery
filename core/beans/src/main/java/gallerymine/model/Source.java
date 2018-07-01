package gallerymine.model;

import gallerymine.model.support.SourceKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document(collection = "source")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Source extends PictureInformation {

    SourceKind kind = SourceKind.UNSET;

    public <FI extends Source> void copyFrom(FI sourceToMatch) {
        super.copyFrom(sourceToMatch);

        kind = sourceToMatch.kind;
    }

}

package gallerymine.model;

import gallerymine.model.support.SourceKind;
import gallerymine.model.support.SourceMatchReport;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Created by sergii_puliaiev on 6/11/18.
 */
@Document(collection = "importSource")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ImportSource extends PictureInformation {

    SourceKind kind = SourceKind.UNSET;

    SourceMatchReport matchReport;

    public <FI extends ImportSource> void copyFrom(FI sourceToMatch) {
        super.copyFrom(sourceToMatch);

        kind = sourceToMatch.kind;
    }
}

package gallerymine.model;

import gallerymine.model.support.SourceKind;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Source extends FileInformation {

    SourceKind kind = SourceKind.UNSET;

}

package gallerymine.model.mvc;

import gallerymine.model.support.PictureGrade;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SourceCriteria extends FileCriteria {

    private String placePath;
    private Double latitude;
    private Double longitude;
    private String distance;
    private PictureGrade grade;

    public SourceCriteria(){
        super();
    }

}

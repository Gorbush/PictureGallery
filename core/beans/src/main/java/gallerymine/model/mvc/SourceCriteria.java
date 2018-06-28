package gallerymine.model.mvc;

import gallerymine.model.support.PictureKind;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.domain.PageRequest;

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
    private PictureKind kind;
    private String requestId;
    private String requestRootId;
    private String processId;

    private PageRequest pager = null;

    public SourceCriteria(){
        pager = new PageRequest(0,5);
    }

}

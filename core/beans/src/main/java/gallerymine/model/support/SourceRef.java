package gallerymine.model.support;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by sergii_puliaiev on 6/20/17.
 */
@Data
public class SourceRef implements Comparable<SourceRef> {

    private String id;
    private PictureGrade grade;

    public SourceRef(){

    }

    public SourceRef(String sourceId, PictureGrade grade) {
        this.grade = grade;
        this.id = sourceId;
    }

    @Override
    public int compareTo(SourceRef o) {
        return StringUtils.compare(o.id, id, true);
    }
}

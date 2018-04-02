package gallerymine.model.importer;

import lombok.Data;
import org.joda.time.DateTime;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Data
public class ThumbRequest {

    private String id;

    private String filePath;
    private String fileName;
    private String source;
    private String error;
    private String thumbName;
    private Boolean inProgress = false;

//    @CreatedDate
    private DateTime created;

    public ThumbRequest() {
    }

    public ThumbRequest(String filePath, String fileName, String thumbName) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.thumbName = thumbName;
    }

}

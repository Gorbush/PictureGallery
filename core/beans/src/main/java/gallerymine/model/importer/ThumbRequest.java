package gallerymine.model.importer;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.util.CollectionUtils;

/**
 * Created by sergii_puliaiev on 6/11/17.
 */
@Document(collection = "thumbRequest")
@Data
public class ThumbRequest {

    @Id
    private String id;

    @Indexed
    private String filePath;
    private String fileName;
    private String source;
    private String error;
    private String thumbName;
    private Boolean inProgress = false;

    @CreatedDate
    private DateTime created;

    public ThumbRequest() {
    }

    public ThumbRequest(String filePath, String fileName, String thumbName) {
        this.filePath = filePath;
        this.fileName = fileName;
        this.thumbName = thumbName;
    }

}

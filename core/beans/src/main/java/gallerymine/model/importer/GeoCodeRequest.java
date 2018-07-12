package gallerymine.model.importer;

import gallerymine.model.GeoPoint;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import static gallerymine.model.importer.GeoCodeRequest.RequestStatus.FOUND;

@Document
@Data
public class GeoCodeRequest {

    public enum RequestStatus {
        FOUND,
        AWAITING,
        EVALUATING,
        FAILED,
        DONE
    }

    @Id
    public String id;

    private String pictureId;
    private GeoJsonPoint point;

    @Indexed
    private RequestStatus status = FOUND;

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

    @Version
    private long version = 0;

    public GeoCodeRequest() {

    }

    public GeoCodeRequest(String pictureId, double longitude, double latitude) {
        this.pictureId = pictureId;
        this.point = new GeoJsonPoint(longitude, latitude);
    }

}

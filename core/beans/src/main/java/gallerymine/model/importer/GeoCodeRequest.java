package gallerymine.model.importer;

import gallerymine.model.GeoPoint;
import lombok.Data;

import static gallerymine.model.importer.GeoCodeRequest.RequestStatus.FOUND;

@Data
public class GeoCodeRequest {

    public enum RequestStatus {
        FOUND,
        AWAITING,
        EVALUATING,
        FAILED,
        DONE
    }

    public String id;

    private String pictureId;
    private GeoPoint point;

    private RequestStatus status = FOUND;

    public GeoCodeRequest() {

    }

    public GeoCodeRequest(String pictureId, double longitude, double latitude) {
        this.pictureId = pictureId;
        this.point = new GeoPoint(longitude, latitude);
    }

}

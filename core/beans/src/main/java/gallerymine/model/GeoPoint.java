package gallerymine.model;

import lombok.Data;

@Data
public class GeoPoint {
    public Double latitude;
    public Double longitude;

    public GeoPoint() {

    }

    public GeoPoint(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }
}

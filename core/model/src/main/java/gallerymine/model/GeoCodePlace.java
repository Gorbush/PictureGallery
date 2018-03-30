package gallerymine.model;

import lombok.Data;

@Data
public class GeoCodePlace {

    private String id;

    private String parentId;

    private String placeName;
    private String placePath;

    private String country;
    private String state;
    private String county;
    private String city;

    private GeoPoint point;
    private GeoPolygon area;

    /** 0- Country, 1-State, 2-county, 3-city */
    public int level = 0;

    public GeoCodePlace() {

    }

    public GeoCodePlace(String placeName, String placePath) {
        this.placeName = placeName;
        this.placePath = placePath;
    }

    public GeoCodePlace(String placeName, String placePath, double longitude, double latitude) {
        this.placeName = placeName;
        this.placePath = placePath;
        this.point = new GeoPoint(longitude, latitude);
    }
    
}

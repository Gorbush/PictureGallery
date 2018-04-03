package gallerymine.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
@Data
public class GeoCodePlace {

    @Id
    private String id;

    private String parentId;

    private String placeName;
    private String placePath;

    private String country;
    private String state;
    private String county;
    private String city;

    private GeoJsonPoint point;
    private GeoJsonPolygon area;

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
        this.point = new GeoJsonPoint(longitude, latitude);
    }
    
}

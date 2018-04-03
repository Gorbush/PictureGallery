package gallerymine.backend.helpers;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.*;
import gallerymine.model.GeoCodePlace;
import gallerymine.model.importer.GeoCodeRequest;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.Polygon;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import gallerymine.backend.beans.repository.GeoCodeRepository;
import gallerymine.backend.beans.repository.GeoCodeRequestRepository;
import java.util.List;

import static org.springframework.data.domain.ExampleMatcher.GenericPropertyMatchers.*;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Created by sergii_puliaiev on 21/01/2016.
 * Based on
 * https://developers.google.com/maps/documentation/javascript/geocoding#ReverseGeocoding
 * https://developers.google.com/maps/documentation/javascript/examples/geocoding-reverse
 *
 * Look into for additional information
 * http://www.geonames.org/export/ws-overview.html
 * http://www.geonames.org/export/web-services.html#findNearbyPlaceName
 * Sample:
 * http://api.geonames.org/findNearbyPlaceNameJSON?formatted=true&lat=37.95007322222222&lng=-122.07149505555554&username=demo&style=full
 *
 * Some other services:
 * http://www.latlong.net/Show-Latitude-Longitude.html
 */
@Component
public class GeoCodeHelper {
    private static final double DISTANCE_CLOSE_POINT = 0.001;
    private static Logger log = LoggerFactory.getLogger(GeoCodeHelper.class);

    static int limitPerDay = 2500;
    static int limitPerSecond = 10;

    static volatile DateTime lastStamp = new DateTime();
    static volatile int today = 0;
    static volatile int second = 0;

    @Autowired
    private GeoCodeRepository geoCodeRepository;

    @Autowired
    private GeoCodeRequestRepository geoCodeRequestRepository;

    @Autowired
//    @Qualifier("tracksMongoOperations")
    public MongoOperations mongoOperations;

    public synchronized GeoCodePlace getPlaceGeocode(String pictureId, double latitude, double longitude) {
        GeoCodePlace result = resolveInDB(latitude, longitude);
        if (result != null) {
            return result;
        }

        DateTime now = new DateTime();
        if (now.getMillis() % 1000 > lastStamp.getMillis() % 1000) {
            second = 0;
        } else {
            if (second >= limitPerSecond) {
                // wait a second
                try {
                    Thread.currentThread().wait(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (lastStamp.withTimeAtStartOfDay().getMillis() > now.withTimeAtStartOfDay().getMillis()) {
            today = 0;
        } else {
            if (today > limitPerDay) {
                log.warn(" geocode resolver limit reached for day. Registering request");
                geoCodeRequestRepository.save(new GeoCodeRequest(pictureId, latitude, longitude));
                return null;
            }
        }
        today++;
        second++;
        GeoApiContext context = new GeoApiContext().setApiKey("AIzaSyBkyXKYbKdEpdJhlnUcypn9ZVI5gOodBNU");
        try {
            GeocodingResult[] results = GeocodingApi.reverseGeocode(context,new LatLng(latitude, longitude)).await();
            result = register(results, latitude, longitude);
        } catch (Exception e) {
            log.error(" Failed to get geocode. Reason: "+e.getMessage(), e);
        }
        return result;
    }

    private GeoCodePlace resolveInDB(double latitude, double longitude) {
        GeoCodePlace place = findByCoords(latitude, longitude);
        if (place != null) {
            return place;
        }
        return null;
    }

    private GeoCodePlace findByCoords(double longitude, double latitude) {
        Query query = query(where("place").near(new Point(longitude, latitude)).maxDistance(DISTANCE_CLOSE_POINT));
        return mongoOperations.findOne(query, GeoCodePlace.class);
    }

    public static AddressComponent findComponentPart(GeocodingResult address, AddressComponentType componentType) {
        for (AddressComponent component : address.addressComponents) {
            if (ArrayUtils.contains(component.types, componentType)) {
                return component;
            }
        }
        return null;
    }

    public static String findComponentPartLongName(GeocodingResult address, AddressComponentType componentType) {
        AddressComponent component = findComponentPart(address, componentType);
        return component == null ? null : component.longName;
    }

    public static GeocodingResult findComponent(GeocodingResult[] geoResult, AddressType addressType) {
        for (GeocodingResult result : geoResult) {
            if (ArrayUtils.contains(result.types, addressType)) {
                return result;
            }
        }
        return null;
    }

    public GeoCodePlace register(GeocodingResult[] geoResult, double latitude, double longitude) {
        if (geoResult != null && geoResult.length > 0) {
            GeocodingResult route = findComponent(geoResult, AddressType.ROUTE);
            GeoCodePlace place;
            if (route == null) {
                place = new GeoCodePlace(geoResult[0].formattedAddress, geoResult[0].formattedAddress, latitude, longitude);
            } else {
                place = new GeoCodePlace(route.formattedAddress, route.formattedAddress, latitude, longitude);
            }
            GeocodingResult address = findComponent(geoResult, AddressType.STREET_ADDRESS);
            if (address == null) {
                address = findComponent(geoResult, AddressType.ROUTE);
            }
            if (address == null) {
                address = geoResult[0];
            }
            if (address != null) {
                place.setCountry(findComponentPartLongName(address, AddressComponentType.COUNTRY));
                place.setState(findComponentPartLongName(address, AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_1));
                place.setCounty(findComponentPartLongName(address, AddressComponentType.ADMINISTRATIVE_AREA_LEVEL_2));
                place.setCity(findComponentPartLongName(address, AddressComponentType.LOCALITY));
            }
            place.setPlacePath("");
            if (StringUtils.isNotBlank(place.getCountry())) {
                place.setPlacePath(place.getPlacePath()+place.getCountry());
            }
            place.setPlacePath(place.getPlacePath()+"/");
            if (StringUtils.isNotBlank(place.getState())) {
                place.setPlacePath(place.getPlacePath()+place.getState());
            }
            place.setPlacePath(place.getPlacePath()+"/");
            if (StringUtils.isNotBlank(place.getCountry())) {
                place.setPlacePath(place.getPlacePath()+place.getCountry());
            }
            place.setPlacePath(place.getPlacePath()+"/");
            if (StringUtils.isNotBlank(place.getCity())) {
                place.setPlacePath(place.getPlacePath()+place.getCity());
            }
            if ("///".equals(place.getPlacePath())) {
                place.setPlacePath(null);
            }

            if (StringUtils.isNotBlank(place.getPlacePath())) {
                registerPlace(place.getPlacePath());
            }
            return geoCodeRepository.save(place);
        }
        return null;
    }

    public GeoCodePlace registerPlace(String placePath) {
        GeoCodePlace present = findByPlace(placePath);

        if (present != null) {
            return present;
        }

        GeoCodePlace parent = null;

        int indexLast = placePath.lastIndexOf("/");
        if (indexLast > 0) {
            String parentPath = placePath.substring(0, indexLast);
            if (StringUtils.isNotBlank(parentPath)) {
                parent = registerPlace(parentPath);
            }
        }

        GeoCodePlace place = new GeoCodePlace(placePath.substring(indexLast + 1), placePath);
        place.level = StringUtils.countMatches(placePath, "/");

        if (parent != null) {
            place.setParentId(parent.getId());
            if (place.level > 0) {
                place.setCountry(parent.getCountry());
            }
            if (place.level > 1) {
                place.setState(parent.getState());
            }
            if (place.level > 2) {
                place.setCountry(parent.getCounty());
            }
        }
        if (place.level == 0) {
            place.setCountry(place.getPlaceName());
        }
        if (place.level == 1) {
            place.setState(place.getPlaceName());
        }
        if (place.level == 2) {
            place.setCounty(place.getPlaceName());
        }
        if (place.level == 3) {
            place.setCity(place.getPlaceName());
        }

        geoCodeRepository.save(place);

        return place;
    }

    public List<GeoCodePlace> findByPointWithin(Polygon polygon) {
        Query query = query(where("place").within(polygon));
        return mongoOperations.find(query, GeoCodePlace.class);
    }

    public GeoCodePlace findByPlace(String placePath) {
        ExampleMatcher matcher = ExampleMatcher.matching().withMatcher("placePath", exact());
        Example<GeoCodePlace> example = Example.of(new GeoCodePlace("", placePath), matcher);
        GeoCodePlace place = geoCodeRepository.findOne(example);
        return place;
    }

    public List<GeoCodePlace> findAllByPlace(String placePath, boolean oneLevel) {
        List<GeoCodePlace> cat;
        ExampleMatcher matcher;
        Example<GeoCodePlace> example;
        if (oneLevel) {
            matcher = ExampleMatcher.matching().withMatcher("placePath", exact());
            example = Example.of(new GeoCodePlace("", placePath), matcher);
            cat = geoCodeRepository.findAll(example);
            if (cat.size() == 1) {
                // matches only one record - let's output it's children
                matcher = ExampleMatcher.matching().withMatcher("placePath", startsWith().ignoreCase());
                example = Example.of(new GeoCodePlace("", placePath), matcher);
                List<GeoCodePlace> children = geoCodeRepository.findAll(example);
                if (children.size() > 0) {
                    cat = children;
                }
            }
        } else {
            matcher = ExampleMatcher.matching().withMatcher("placePath", startsWith().ignoreCase());
            example = Example.of(new GeoCodePlace("", placePath), matcher);
            cat = geoCodeRepository.findAll(example);
        }
        return cat;
    }
}

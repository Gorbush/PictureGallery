package gallerymine.backend.helpers.analyzer;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.exif.*;
import com.drew.metadata.exif.makernotes.CanonMakernoteDirectory;
import com.drew.metadata.exif.makernotes.KodakMakernoteDirectory;
import com.drew.metadata.file.FileMetadataDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.photoshop.PsdHeaderDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.xmp.XmpDirectory;
import gallerymine.backend.beans.AppConfig;
import gallerymine.model.FileInformation;
import gallerymine.model.GeoPoint;
import gallerymine.model.PictureInformation;
import gallerymine.model.Source;
import gallerymine.model.support.ImageInformation;
import gallerymine.model.support.TimestampKind;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Analyser for Image files
 * Created by sergii_puliaiev on 6/11/17.
 */
@Component
public class ImageFormatAnalyser {
    private static Logger log = LoggerFactory.getLogger(ImageFormatAnalyser.class);
//    private static Logger logUnknownDirectory = LogManager.getLogger("unknownDirectory");
    private static Logger logUnknownDirectory = LoggerFactory.getLogger(ImageFormatAnalyser.class);

    /** format IMG_20160812_163115.jpg */
    private Pattern parser1 = Pattern.compile("([1,2][0-9][0-9][0-9][0-1][0-9][0-3][0-9]_[0-2][0-9][0-5][0-9][0-5][0-9])");
    /** format IMG_06012017_182643.png */
    private Pattern parser2 = Pattern.compile("([0-1][0-9][0-3][0-9][1,2][0-9][0-9][0-9]_[0-2][0-9][0-5][0-9][0-5][0-9])");

    public static Collection<String> allowedExtensions = new HashSet<String>(){{
        add("jpg");
        add("jpeg");
        add("png");
//        add("mp4");
        add("tiff");
        add("psd");
        add("bmp");
        add("gif");
//        add("pdf");
//        add("svg");
    }};

    @Autowired
    private AppConfig appConfig;

    public void gatherFileInformation(Path file, Path rootFolder, PictureInformation source, boolean extractThumb) {
        try {
            ImageInformation info = new ImageInformation();
            // preset some properties to avoid re-population
            info.extractThumb = extractThumb;
            info.file = file.toFile();

            if (StringUtils.isNotBlank(source.getThumbPath())) {
                info.thumbFile = new File(source.getThumbPath());
            }

            info = readImageInformation(info);

            if (info != null) {
                source.addStamps(info.timestamps);

                source.setHeight(info.height);
                source.setWidth(info.width);
                source.setOrientation(info.orientation);
                if (info.thumbFile != null && !source.hasThumb()) {
                    source.setThumbPath(info.thumbFile.getAbsolutePath());
                }
                if (info.latitude != null && info.longitude != null) {
                    source.setGeoLocation(new GeoJsonPoint(info.longitude, info.latitude));
                } else {
                    source.setGeoLocation(null);
                }
            }
        } catch (Exception e){
            source.setFilled(true);
            source.setExists(true);
            source.setError("Failed to gather info: "+e.getMessage());
            log.error("Failed to gather info for id={} name={} in path {}. Reason: {}", source.getId(), source.getFileName(), source.getFilePath() ,e.getMessage(), e);
        }
    }

    private ImageInformation readImageInformation(ImageInformation info) throws Exception {
        Metadata metadata = ImageMetadataReader.readMetadata(info.file);

        for(Directory directory: metadata.getDirectories()) {
            populateDirectoryInfo(info, directory);
        }

        return info;
    }

    private void populateDirectoryInfo(ImageInformation info, Directory directory) throws Exception {
        log.info("Found image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
        try {
            switch (directory.getClass().getSimpleName()) {
                case "ExifThumbnailDirectory": {
                    if (info.extractThumb) {
                        info.thumbFile = Files.createTempFile("thumbnail", ".jpg").toFile();
                        ((ExifThumbnailDirectory) directory).writeThumbnail(info.thumbFile.getAbsolutePath());
                        log.debug(" thumbnail: {}", info.thumbFile.getAbsolutePath());
                    } else {
                        log.debug("Thumbnail is not requested");
                    }
                    break;
                }
                case "ExifIFD0Directory": {
                    info.addStamp(TimestampKind.TS_FILE_EXIF_ORIGINAL.create(directory.getDate(ExifIFD0Directory.TAG_DATETIME_ORIGINAL)));
                    info.addStamp(TimestampKind.TS_FILE_EXIF.create(directory.getDate(ExifIFD0Directory.TAG_DATETIME)));
                    info.orientation = 1;
                    if (directory.containsTag(ExifDirectoryBase.TAG_ORIENTATION)) {
                        info.orientation = directory.getInt(ExifDirectoryBase.TAG_ORIENTATION);
                    }
                    String model = directory.getString(ExifDirectoryBase.TAG_MODEL);
                    if (StringUtils.isNotBlank(model)) {
                        info.device = model;
                    }
                    break;
                }
                case "FileMetadataDirectory": {
                    // get FileMetadata
                    if (directory.containsTag(FileMetadataDirectory.TAG_FILE_MODIFIED_DATE)) {
                        info.addStamp(TimestampKind.TS_FILE_MODIFY.create(directory.getDate(FileMetadataDirectory.TAG_FILE_MODIFIED_DATE)));
                    }
                    break;
                }
                case "GpsDirectory": {
                    if (directory.containsTag(GpsDirectory.TAG_TIME_STAMP) && directory.containsTag(GpsDirectory.TAG_DATE_STAMP)) {
                        // format like 2016:01:16
                        String createDate = directory.getString(GpsDirectory.TAG_DATE_STAMP);
                        if (StringUtils.isNotBlank(createDate)) {
                            int[] time = directory.getIntArray(GpsDirectory.TAG_TIME_STAMP);
                            createDate += "T" + time[0] + ":" + time[1] + ":" + time[2];

                            LocalDateTime dt = LocalDateTime.parse(createDate, DateTimeFormatter.ofPattern("yyyy':'MM':'dd'T'H':'m':'s"));
                            info.addStamp(TimestampKind.TS_GPS.create(dt.atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli()));
                        }
                    }
                    GeoLocation geoLocation = ((GpsDirectory) directory).getGeoLocation();
                    if (geoLocation != null) {
                        info.latitude = geoLocation.getLatitude();
                        info.longitude = geoLocation.getLongitude();
                    }
                    break;
                }
                case "JpegDirectory": {
                    if (directory.containsTag(JpegDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(JpegDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(JpegDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(JpegDirectory.TAG_IMAGE_HEIGHT);
                    }
                    break;
                }
                case "PngDirectory": {
                    if (directory.containsTag(PngDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(PngDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(PngDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(PngDirectory.TAG_IMAGE_HEIGHT);
                    }
                    if (directory.containsTag(PngDirectory.TAG_LAST_MODIFICATION_TIME)) {
                        info.addStamp(TimestampKind.TS_FILE_EXIF.create(directory.getDate(PngDirectory.TAG_LAST_MODIFICATION_TIME)));
                    }
                    break;
                }
                case "ExifSubIFDDirectory": {
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                        info.addStamp(TimestampKind.TS_FILE_EXIF_ORIGINAL.create(directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)));
                    }
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)) {
                        info.addStamp(TimestampKind.TS_FILE_EXIF.create(directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED)));
                    }
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(ExifSubIFDDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH)) {
                        info.width = directory.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(ExifSubIFDDirectory.TAG_IMAGE_HEIGHT);
                    }
                    if (directory.containsTag(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
                    }
                    String model = directory.getString(ExifSubIFDDirectory.TAG_MODEL);
                    if (StringUtils.isNotBlank(model)) {
                        info.device = model;
                    }
                    break;
                }
                case "JfifDirectory": {
//                log.info("JfifDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "ExifInteropDirectory": {
//                log.info("ExifInteropDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "XmpDirectory": {
//                log.info("XmpDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    XmpDirectory directoryXMP = ((XmpDirectory) directory);
                    String creatorTool = directoryXMP.getXmpProperties().get("xmp:CreatorTool");
                    if (StringUtils.isBlank(info.device) && StringUtils.isNotBlank(creatorTool)) {
                        info.device = creatorTool;
                    }
                    String createDate = directoryXMP.getXmpProperties().get("xmp:CreateDate");
                    if (StringUtils.isNotBlank(createDate) && !info.hasTimeStamp(TimestampKind.TS_FILE_EXIF)) {
                        long dt = LocalDateTime.parse(createDate.replaceAll("\\.[0-9]+$", ""), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")).atZone(ZoneId.of("America/Los_Angeles")).toInstant().toEpochMilli();
                        info.addStamp(TimestampKind.TS_FILE_EXIF.create(dt));
                    }
                    break;
                }
                case "PhotoshopDirectory": {
//                log.info("PhotoshopDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "IptcDirectory": {
//                log.info("IptcDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    if (directory.containsTag(IptcDirectory.TAG_DATE_CREATED) && directory.containsTag(IptcDirectory.TAG_TIME_CREATED)) {
                        DateTime dt = new DateTime(directory.getDate(IptcDirectory.TAG_DATE_CREATED));
                        String timeString = directory.getString(IptcDirectory.TAG_TIME_CREATED);
                        Integer time = Integer.parseInt(timeString.replaceAll("[Z+-].*$", ""));
                        String zoneString = timeString.replaceAll("^.*([Z+-])", "$1");
                        if (zoneString.length() > 0 ) {
                            Integer zone = Integer.parseInt(zoneString);
                            dt = dt.withZone(DateTimeZone.forOffsetHoursMinutes(zone / 100 % 100, zone % 100));
                        }
                        dt = dt.withHourOfDay(time / 10000)
                                .withMinuteOfHour(time / 100 % 100)
                                .withSecondOfMinute(time % 100);

                        info.addStamp(TimestampKind.TS_FILE_EXIF.create(dt));
                    }
                    if (directory.containsTag(IptcDirectory.TAG_DIGITAL_DATE_CREATED) && directory.containsTag(IptcDirectory.TAG_DIGITAL_TIME_CREATED)) {
                        DateTime dt = new DateTime(directory.getDate(IptcDirectory.TAG_DIGITAL_DATE_CREATED));
                        String timeString = directory.getString(IptcDirectory.TAG_DIGITAL_TIME_CREATED);
                        Integer time = Integer.parseInt(timeString.replaceAll("[Z+-].*$", ""));
                        if (timeString.matches(".*[Z+-].*")) {
                            String zoneString = timeString.replaceAll("([Z+-].*)$", "$1");
                            Integer zone = Integer.parseInt(zoneString);
                            dt = dt.withZone(DateTimeZone.forOffsetHoursMinutes(zone / 100 % 100, zone % 100));
                        }
                        dt = dt.withHourOfDay(time / 10000)
                                .withMinuteOfHour(time / 100 % 100)
                                .withSecondOfMinute(time % 100)
                                .withMillis(0);
                        info.addStamp(TimestampKind.TS_FILE_EXIF_ORIGINAL.create(dt));
                    }
                    break;
                }
                case "IccDirectory": {
//                log.info("IccDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "KodakMakernoteDirectory": {
                    log.info("KodakMakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    KodakMakernoteDirectory directoryCustom = ((KodakMakernoteDirectory) directory);
                    String creatorTool = directoryCustom.getString(KodakMakernoteDirectory.TAG_KODAK_MODEL);
                    if (StringUtils.isBlank(info.device)) {
                        if (StringUtils.isNotBlank(creatorTool)) {
                            info.device = "Kodak " + creatorTool;
                        } else {
                            info.device = "Kodak";
                        }
                    }
                    if (directory.containsTag(KodakMakernoteDirectory.TAG_YEAR_CREATED)
                            && directory.containsTag(KodakMakernoteDirectory.TAG_MONTH_DAY_CREATED)
                            && directory.containsTag(KodakMakernoteDirectory.TAG_TIME_CREATED)) {
                        int year = directory.getInt(KodakMakernoteDirectory.TAG_YEAR_CREATED);
                        int[] md = directory.getIntArray(KodakMakernoteDirectory.TAG_MONTH_DAY_CREATED);
                        int[] tm = directory.getIntArray(KodakMakernoteDirectory.TAG_TIME_CREATED);
                        DateTime dt = new DateTime()
                                .withDayOfMonth(1)
                                .withYear(year)
                                .withMonthOfYear(md[0])
                                .withDayOfMonth(md[1])
                                .withHourOfDay(tm[0])
                                .withMinuteOfHour(tm[1])
                                .withSecondOfMinute(tm[2]);

                        info.addStamp(TimestampKind.TS_FILE_EXIF.create(dt));
                    }
                    if (directory.containsTag(KodakMakernoteDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(KodakMakernoteDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(KodakMakernoteDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(KodakMakernoteDirectory.TAG_IMAGE_HEIGHT);
                    }

                    break;
                }
                case "AdobeJpegDirectory": {
                    log.info("AdobeJpegDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "BmpHeaderDirectory": {
                    log.info("BmpHeaderDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    if (directory.containsTag(BmpHeaderDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(BmpHeaderDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(BmpHeaderDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(BmpHeaderDirectory.TAG_IMAGE_HEIGHT);
                    }
                    break;
                }
                case "CanonMakernoteDirectory": {
                    log.info("CanonMakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Cannon";
                    }
                    CanonMakernoteDirectory directoryCustom = ((CanonMakernoteDirectory) directory);
                    String creatorTool = directoryCustom.getString(CanonMakernoteDirectory.TAG_CANON_IMAGE_TYPE);
                    if (StringUtils.isNotBlank(creatorTool)) {
                        info.device = creatorTool;
                    }
                    break;
                }
                case "CasioType2MakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Casio";
                    }
                    log.info("CasioType2MakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "FujifilmMakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Fujifilm";
                    }
                    log.info("FujifilmMakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "JpegCommentDirectory": {
                    log.info("JpegCommentDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "NikonType2MakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Nikon";
                    }
                    log.info("NikonType2MakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "OlympusMakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Olympus";
                    }
                    log.info("OlympusMakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "PanasonicMakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Panasonic";
                    }
                    log.info("PanasonicMakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                case "PsdHeaderDirectory": {
                    log.info("PsdHeaderDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    if (directory.containsTag(PsdHeaderDirectory.TAG_IMAGE_WIDTH)) {
                        info.width = directory.getInt(PsdHeaderDirectory.TAG_IMAGE_WIDTH);
                    }
                    if (directory.containsTag(PsdHeaderDirectory.TAG_IMAGE_HEIGHT)) {
                        info.height = directory.getInt(PsdHeaderDirectory.TAG_IMAGE_HEIGHT);
                    }
                    break;
                }
                case "SonyType1MakernoteDirectory": {
                    if (StringUtils.isBlank(info.device)) {
                        info.device = "Sony";
                    }
                    log.info("SonyType1MakernoteDirectory image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    break;
                }
                default: {
                    log.error("Unknown image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                    logUnknownDirectory.error("Unknown image directory: {} in file {}", directory.getClass().getSimpleName(), info.file.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            log.error("Error processing file {}. Reason: {}", info.file.getAbsolutePath(), e.getMessage(), e);
        }
    }

    public boolean acceptsExtension(String fileExt) {
        return allowedExtensions.contains(fileExt.toLowerCase());
    }
}

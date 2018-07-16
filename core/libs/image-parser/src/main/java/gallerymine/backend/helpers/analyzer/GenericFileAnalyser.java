package gallerymine.backend.helpers.analyzer;

import gallerymine.backend.beans.AppConfig;
import gallerymine.model.FileInformation;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.Timestamp;
import gallerymine.model.support.TimestampKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyser for Image files
 * Created by sergii_puliaiev on 6/20/18.
 */
@Component
public class GenericFileAnalyser {

    private static Logger log = LoggerFactory.getLogger(ImageFormatAnalyser.class);
    //    private static Logger logUnknownDirectory = LogManager.getLogger("unknownDirectory");
    private static Logger logUnknownDirectory = LoggerFactory.getLogger(ImageFormatAnalyser.class);

    public static final String KIND_FILE = "File";

    private static List<StampMatcher> matchers = new ArrayList<>();
    static {{
        /** format IMG_20160812_163115.jpg           Year 1900-2099     month 00-12 Day 00-31  00-23    00-59     00-59   */
        matchers.add(new StampMatcher("([1,2][0,9][0-9][0-9][0,1][0-9][0-3][0-9]_[0-2][0-9][0-5][0-9][0-5][0-9])",
                "yyyyMMdd'_'HHmmss"));
        /** format IMG_06012017_182643.png         month 00-12 Day 00-31  Year 1900-2099      00-23     00-59     00-59   */
        matchers.add(new StampMatcher("([0,1][0-9][0-3][0,9][1,2][0,9][0-9][0-9]_[0-2][0-9][0-5][0-9][0-5][0-9])",
                "ddMMyyyy'_'HHmmss"));
        /** format 2018-06-21_11-32-40_234.heic     Year 1900-2099       month 00-12 Day 00-31 00-23      00-59      00-59  */
        matchers.add(new StampMatcher("([0-2][0,9][0-9][0-9]-[0,1][0-9]-[0-9][0-9]_[0-2][0-9]-[0-5][0-9]-[0-5][0-9]_[0-9][0-9][0-9])",
                "yyyy-MM-dd'_'HH-mm-ss'_'SSS"));
    }}

    private final AppConfig appConfig;

    private static class StampMatcher {
        String checkPattern;
        String parsePattern;

        Pattern parser;

        StampMatcher(String checkPattern, String parsePattern) {
            this.checkPattern = checkPattern;
            this.parsePattern = parsePattern;
            this.parser = Pattern.compile(checkPattern);
        }

        Timestamp getStamp(String fileName) {
            try {
                Matcher dt = parser.matcher(fileName);
                if (dt.find()) {
                    String stampPart = dt.group(1);
                    return TimestampKind.TS_FILE_NAME.create(LocalDateTime.parse(stampPart,
                            DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmmss")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                }
            } catch (Exception e) {
                // ignore all parsing errors for now - they are not important
            }
            return null;
        }
    }

    @Autowired
    public GenericFileAnalyser(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    public void gatherFileInformation(Path file, Path importRootFolder, FileInformation info) {
        try {
            // preset some properties to avoid re-population
            Path fullImportPath = appConfig.getImportRootFolderPath().resolve(importRootFolder);
            info.setRootPath(importRootFolder.toString());
            info.setFilePath(appConfig.relativizePath(file.getParent(), fullImportPath));
            info.setFileName(file.toFile().getName());
            info.setOriginalFileName(file.toFile().getName());
            info.setSize(file.toFile().length());
            info.setStatus(InfoStatus.ANALYSING);

            info.setFilled(true);
            info.setExists(true);
            info.setError(null);

            fetchStampsFromFileAndName(file, info);

            info.updateTimestamp();

            info.getPopulatedBy().add(KIND_FILE);
        } catch (Exception e){
            info.setFilled(true);
            info.setExists(true);
            info.setError("Failed to gather info: "+e.getMessage());
            log.error("Failed to gather info for name={} in path {}. Reason: {}", file.getFileName(), file.toFile().getPath(), e.getMessage(), e);
        }
    }

    private void fetchStampsFromFileAndName(Path file, FileInformation info) {
        try {
            Path javaPath = Paths.get(file.toFile().getAbsolutePath());
            BasicFileAttributes view = Files.getFileAttributeView(javaPath, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();

            info.addStamp(TimestampKind.TS_FILE_CREATE.create(view.creationTime().toMillis()));
        } catch (Exception e){
            log.warn("Failed to index basic file attributes for {}", file.toFile().getAbsolutePath(), e);
        }

        try {
            String fileName = file.toFile().getName();

            for(StampMatcher matcher : matchers) {
                Timestamp stamp = matcher.getStamp(fileName);
                if (stamp != null) {
                    info.addStamp(stamp);
                }
            }
        } catch (Exception e){
            log.warn("Failed to index basic file attributes for {}", file.toFile().getAbsolutePath(), e);
        }
    }
}

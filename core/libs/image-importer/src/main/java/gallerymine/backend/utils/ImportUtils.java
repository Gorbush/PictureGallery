package gallerymine.backend.utils;

import gallerymine.backend.beans.AppConfig;
import gallerymine.model.Source;
import org.apache.commons.io.FileUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ImportUtils {

    public static final String IMPORT_FILE_NAME = "import.mark";
    public static final String IMPORT_TIMESTAMP = "import.mark";

    private static Logger log = LoggerFactory.getLogger(ImportUtils.class);

    @Autowired
    public AppConfig appConfig;

    public Path makeNewImportStamp(DateTime importStamp) {
        String stamp = importStamp.toString("yyyy-MM-dd_HH-mm-ss");
        Path importFolder = Paths.get(appConfig.importRootFolder, stamp);
        int index = 0;
        while (importFolder.toFile().exists()) {
            index++;
            importFolder = Paths.get(appConfig.importRootFolder, stamp+"_"+index);
        }
        return importFolder;
    }

    public Path getImportMarkFile(Path path) {
        Path markFile = path.resolve(IMPORT_FILE_NAME);
        return markFile;
    }

    public String getImportMarkFileContent(Path path) {
        Path markFile = getImportMarkFile(path);
        try {
            return FileUtils.readFileToString(markFile.toFile(), "UTF-8");
        } catch (IOException e) {
            log.error("Failed to read the mark file %s", markFile.toFile().getAbsolutePath());
            return null;
        }
    }

    public Path createImportMarkFile(Path path, DateTime importStamp) {
        Path markFile = path.resolve(IMPORT_FILE_NAME);
        if (!markFile.toFile().exists()) {
            String stamp = importStamp.toString("yyyy-MM-dd HH:mm:ss");
            try {
                FileUtils.writeStringToFile(markFile.toFile(), stamp+"\n", "UTF-8");
            } catch (IOException e) {
                log.error("Failed to write to Import Mark File %s", markFile.toFile().getAbsolutePath());
            }
        }
        return markFile;
    }

    public void appendImportMarkFile(Path markFileOrFolder, String text) {
        if (markFileOrFolder.toFile().exists()) {
            Path markFile = markFileOrFolder;
            if (markFileOrFolder.toFile().isDirectory()) {
                markFile = markFileOrFolder.resolve(IMPORT_FILE_NAME);
            }
            try {
                FileUtils.writeStringToFile(markFile.toFile(), text+"\n", "UTF-8", true);
            } catch (IOException e) {
                log.error("Failed to append to Import Mark File %s", markFile.toFile().getAbsolutePath());
            }
        }
    }

    public Path generatePicThumbName(String fileName, DateTime timestamp) {
        if (timestamp == null) {
            timestamp = new DateTime();
        }

        String picFolderName = timestamp.toString("/yyyy/MM/dd/SSSSSSSSS"); //SSSSSSSSS
        Path thumbFolder = Paths.get(appConfig.getThumbsRootFolder(), picFolderName);

        if (!thumbFolder.toFile().exists()) {
            boolean created = thumbFolder.toFile().mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create a folder for thumbnail path='" + thumbFolder.toFile().getAbsolutePath() + "'");
            }
        }

        String picFileName = timestamp.getMillis()+"_"+fileName;

        Path thumbPath = thumbFolder.resolve(picFileName+".jpg");
        int index = 0;
        while(thumbPath.toFile().exists()) {
            index++;
            thumbPath = thumbFolder.resolve(picFileName+"_"+index+".jpg");
        }
        return thumbPath;
    }
}

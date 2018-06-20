package gallerymine.backend.beans;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
//@RefreshScope
@Data
public class AppConfig {

    @Value("${galleryRootFolder}")
    public String galleryRootFolder;

    @Value("${sourcesRootFolder}")
    public String sourcesRootFolder;

    @Value("${thumbsRootFolder}")
    public String thumbsRootFolder;

    @Value("${importRootFolder}")
    public String importRootFolder;

    @Value("${importExposedRootFolder}")
    public String importExposedRootFolder;

    public String getGalleryRootFolder() {
        checkFolders();
        return galleryRootFolder;
    }

    public String getSourcesRootFolder() {
        checkFolders();
        return sourcesRootFolder;
    }

    public String getThumbsRootFolder() {
        checkFolders();
        return thumbsRootFolder;
    }

    public String getImportRootFolder() {
        checkFolders();
        return importRootFolder;
    }

    public String getImportExposedRootFolder() {
        checkFolders();
        return importExposedRootFolder;
    }

    public String relativizePathToGallery(String filePath) throws FileNotFoundException {
        return relativizePath(filePath, galleryRootFolder);
    }

    public String relativizePathToSource(String filePath) throws FileNotFoundException {
        return relativizePath(filePath, sourcesRootFolder);
    }

    public String relativizePathToThumb(String filePath) throws FileNotFoundException {
        return relativizePath(filePath, thumbsRootFolder);
    }

    public String relativizePathToImport(String filePath) throws FileNotFoundException {
        return relativizePath(filePath, importRootFolder);
    }

    public String relativizePath(String fileName, String rootFolder) throws FileNotFoundException {
        checkFolders();
        Path root = Paths.get(rootFolder);
        if (root == null || !root.toFile().exists()) {
            throw new FileNotFoundException("Root folder not found: "+rootFolder);
        }
        Path file = Paths.get(fileName);
        Path relative = root.relativize(file);
        String relativePath = relative.toString();
        if (!relativePath.startsWith("/")) {
            relativePath = "/"+relativePath;
        }
        return relativePath;
    }

    private String amendFolder(String folder) {
        if (folder != null && folder.startsWith("~")) {
            folder = folder.replace("~", System.getenv("HOME"));
        }
        return folder;
    }

    private void checkFolders() {
        galleryRootFolder = amendFolder(galleryRootFolder);
        sourcesRootFolder = amendFolder(sourcesRootFolder);
        thumbsRootFolder = amendFolder(thumbsRootFolder);
        importRootFolder = amendFolder(importRootFolder);
        importExposedRootFolder = amendFolder(importExposedRootFolder);
    }

}

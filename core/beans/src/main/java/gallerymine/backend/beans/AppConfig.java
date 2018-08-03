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

    @Value("${gallery.paths.galleryRootFolder}")
    public String galleryRootFolder;

    @Value("${gallery.paths.sourcesRootFolder}")
    public String sourcesRootFolder;

    @Value("${gallery.paths.thumbsRootFolder}")
    public String thumbsRootFolder;

    @Value("${gallery.paths.importRootFolder}")
    public String importRootFolder;

    @Value("${gallery.paths.importExposedRootFolder}")
    public String importExposedRootFolder;

    @Value("${gallery.paths.importTestRootFolder}")
    public String importTestRootFolder;

    @Value("${gallery.debug.dryRunImportMoves}")
    public boolean dryRunImportMoves = true;

    @Value("${gallery.import.abandoned_timeout_ms}")
    public long abandonedTimoutMs;

    @Value("${gallery.import.disableThumbs}")
    public boolean disableThumbs = false;

    Path importRootFolderPath;

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

    public Path getImportRootFolderPath() {
        checkFolders();
        return importRootFolderPath;
    }

    public String getImportExposedRootFolder() {
        checkFolders();
        return importExposedRootFolder;
    }

    public String getImportTestRootFolder() {
        checkFolders();
        return importTestRootFolder;
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

    public String relativizePathToGallery(Path filePath) throws FileNotFoundException {
        return relativizePath(filePath, Paths.get(galleryRootFolder));
    }

    public String relativizePathToSource(Path filePath) throws FileNotFoundException {
        return relativizePath(filePath, Paths.get(sourcesRootFolder));
    }

    public String relativizePathToThumb(Path filePath) throws FileNotFoundException {
        return relativizePath(filePath, Paths.get(thumbsRootFolder));
    }

    public String relativizePathToImport(Path filePath) throws FileNotFoundException {
        return relativizePath(filePath, Paths.get(importRootFolder));
    }

    public String relativizePath(String fileName, String rootFolder) throws FileNotFoundException {
        return relativizePath(Paths.get(fileName), Paths.get(rootFolder));
    }

    public String relativizePath(Path file, Path rootFolder) throws FileNotFoundException {
        checkFolders();
        if (!file.isAbsolute()) {
            return file.toString();
        }
        if (rootFolder == null || !rootFolder.toFile().exists()) {
            throw new FileNotFoundException("Root folder not found: "+rootFolder);
        }
        Path relative = rootFolder.relativize(file);
        String relativePath = relative.toString();
        if (relativePath.startsWith("/") && relativePath.length() > 1) {
            relativePath = relativePath.substring(1);
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
        importTestRootFolder = amendFolder(importTestRootFolder);

        if (importRootFolderPath == null) {
            importRootFolderPath = Paths.get(importRootFolder);
        }
    }

}

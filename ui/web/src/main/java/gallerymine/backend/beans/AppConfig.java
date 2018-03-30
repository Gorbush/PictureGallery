package gallerymine.backend.beans;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
public class AppConfig {

    @Value("${galleryRootFolder}")
    public String galleryRootFolder;

    @Value("${sourcesRootFolder}")
    public String sourcesRootFolder;

    @Value("${thumbsRootFolder}")
    public String thumbsRootFolder;

    public String getGalleryRootFolder() {
        return galleryRootFolder;
    }

    public String getSourcesRootFolder() {
        return sourcesRootFolder;
    }

    public String getThumbsRootFolder() {
        return thumbsRootFolder;
    }

    public String relativizePathToGallery(String filePath) {
        return relativizePath(filePath, galleryRootFolder);
    }

    public String relativizePathToSource(String filePath) {
        return relativizePath(filePath, sourcesRootFolder);
    }

    public String relativizePathToThumb(String filePath) {
        return relativizePath(filePath, thumbsRootFolder);
    }

    public String relativizePath(String fileName, String rootFolder) {
        Path root = Paths.get(rootFolder);
        Path file = Paths.get(fileName);
        Path relative = root.relativize(file);
        String relativePath = relative.toString();
        if (!relativePath.startsWith("/")) {
            relativePath = "/"+relativePath;
        }
        return relativePath;
    }

}

package gallerymine.backend.analyzer;

import gallerymine.model.FileInformation;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Path;

public abstract class BaseAnalyser {

    public boolean acceptsFile(String fileName) {
        String ext = FilenameUtils.getExtension(fileName);
        return acceptsExtension(ext);
    }

    public boolean acceptsExtension(String fileExt) {
        return false;
    }

    public boolean gatherFileInformation(Path file, FileInformation info) {
        return false;
    }
}
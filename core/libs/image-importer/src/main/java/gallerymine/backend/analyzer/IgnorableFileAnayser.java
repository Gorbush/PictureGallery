package gallerymine.backend.analyzer;

import com.google.common.collect.Sets;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class IgnorableFileAnayser {

    public static final Set<String> skippableFiles = Sets.newHashSet(
            ".DS_Store",
            "read.me",
            "README.md"
    );

    public static final Set<String> skippableExtensions = Sets.newHashSet(
            "txt"
    );

    public boolean accepts(String fileName) {
        if (fileName.startsWith(".")) {
            // means hidden or system file - skip
            return true;
        }

        if (skippableFiles.contains(fileName)) {
            return true;
        }

        String fileExt = FilenameUtils.getExtension(fileName);

        if (skippableExtensions.contains(fileExt)) {
            return true;
        }
        return false;
    }
}

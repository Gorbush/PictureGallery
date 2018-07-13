package gallerymine.backend.helpers.analyzer;

import com.google.common.collect.Sets;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class IgnorableFileAnayser {

    public static final Set<String> skippableFiles = Sets.newHashSet(
            ".DS_Store"
    );

    public static final Set<String> skippableExtensions = Sets.newHashSet(
            ".DS_Store"
    );

    public boolean accepts(String fileName) {
        if (fileName.startsWith(".")) {
            // means hidden or system file - skip
            return true;
        }

        if (skippableFiles.contains(fileName)) {
            return true;
        }

        String fileExt = fileName.substring(fileName.lastIndexOf(".") + 1);

        if (skippableExtensions.contains(fileExt)) {
            return true;
        }
        return false;
    }
}

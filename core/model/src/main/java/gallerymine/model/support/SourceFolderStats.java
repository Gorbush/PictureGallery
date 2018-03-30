package gallerymine.model.support;

import lombok.Data;

/**
 * Created by sergii_puliaiev on 6/24/17.
 */
@Data
public class SourceFolderStats {

    String path;

    boolean exists;

    long files = 0;
    long subfolders = 0;

    long filesNotExisting;
    long filesDuplicates;
    long filesExistingNotMatched;
    long filesExistingMatched;
    long filesMatched;
    long filesNotMatched;

    public void incFiles() {
        files++;
    }

    public void incSubfolders() {
        subfolders++;
    }

    public void incFilesNotExisting() {
        filesNotExisting++;
    }

    public void incFilesDuplicates() {
        filesDuplicates++;
    }

    public void incFilesExistingNotMatched() {
        filesExistingNotMatched++;
    }

    public void incFilesExistingMatched() {
        filesExistingMatched++;
    }

    public void incFilesMatched() {
        filesMatched++;
    }

    public void incFilesNotMatched() {
        filesNotMatched++;
    }

}

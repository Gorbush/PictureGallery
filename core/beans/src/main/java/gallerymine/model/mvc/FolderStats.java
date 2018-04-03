package gallerymine.model.mvc;

import java.io.Serializable;

public class FolderStats implements Serializable {

    private static final long serialVersionUID = 1L;

    String name;

    String fullPath;

    Long count;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}

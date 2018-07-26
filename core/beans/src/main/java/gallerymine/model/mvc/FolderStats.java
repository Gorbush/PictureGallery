package gallerymine.model.mvc;

import lombok.Data;

import java.io.Serializable;

@Data
public class FolderStats implements Serializable {

    private static final long serialVersionUID = 1L;

    String name;
    String parentName;
    String fullPath;
    Long filesCount;
    Long foldersCount;

}

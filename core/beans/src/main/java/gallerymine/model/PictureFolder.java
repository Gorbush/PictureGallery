package gallerymine.model;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This bean holds information about request for indexing
 * Created by sergii_puliaiev on 7/30/18.
 */
@Document(collection = "pictureFolder")
@Data
public class PictureFolder {

    @Id
    private String id;

    @Indexed
    private String name;
    @Indexed
    private String namel;
    @Indexed
    private String parentId;

    long filesCount;
    long foldersCount;

    /** Current folder path with name, in lower case to support all filesystems */
    @Indexed(unique = true)
    private String fullPath;

    /** Current folder path with name, in lower case to support all filesystems */
    @Indexed
    private String path;

    private List<String> notes = new ArrayList<>();

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

    @Version
    private Long version;

    public PictureFolder() {
    }

    public String addNote(String note, Object... params) {
        if (params!= null && params.length > 0) {
            note = String.format(note, params);
        }
        notes.add(note);
        return note;
    }

    public String notesText() {
        return notes.stream().collect(Collectors.joining("\n"));
    }

    public void setName(String name) {
        this.name = name;
        namel = name == null ? null : name.toLowerCase().replaceAll("^[_$/\\\\]*", "");
    }

}

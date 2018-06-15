package gallerymine.model;

//import com.mysema.query.annotations.QueryEntity;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

@Document
//@QueryEntity
@Data
public class Process {
    @Id
    private String id;

    private DateTime started;
    private DateTime finished;

    private String name;
    private ProcessType type;

    private ProcessStatus status;

    @CreatedDate
    private DateTime created;
    @LastModifiedDate
    private DateTime updated;

}

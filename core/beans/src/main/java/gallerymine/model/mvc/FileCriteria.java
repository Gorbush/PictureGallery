package gallerymine.model.mvc;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gallerymine.model.support.InfoStatus;
import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@Data
public class FileCriteria {

    private DateTime fromDate;
    private DateTime toDate;
    private String path;
    private String folderId;
    private String fileName;
    private Long fileSize;
    private DateTime timestamp;
    private List<DateTime> timestamps;
    private String sortByField;
    private Boolean sortDescending;
    private String requestId;
    private String requestRootId;
    private String processId;
    private Set<String> populatedBy;
    private Set<String> populatedNotBy;
    private InfoStatus status;
    private Set<InfoStatus> statuses;

    @JsonIgnore
    private PageRequest pager;

    public FileCriteria(){
        pager = new PageRequest(0,5);
    }

    public int getPage() {
        return pager.getPageNumber();
    }

    public void setPage(int page) {
        pager = new PageRequest(page,pager.getPageSize());
    }

    public int getSize() {
        return pager.getPageSize();
    }

    public void maxSize() {
        setSize(Integer.MAX_VALUE);
    }

    public void setSize(int size) {
        pager = new PageRequest(pager.getPageNumber(), size);
    }

    public int getOffset() {
        return pager.getPageNumber() * pager.getPageSize();
    }

    public void addStatuses(InfoStatus... statuses) {
        if (this.statuses == null) {
            this.statuses = new HashSet<>(Arrays.asList(statuses));
        } else {
            this.statuses.addAll(new HashSet<>(Arrays.asList(statuses)));
        }
    }

}

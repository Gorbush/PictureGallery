package gallerymine.model.mvc;

import lombok.Data;
import org.joda.time.DateTime;
import org.springframework.data.domain.PageRequest;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@Data
public class SourceCriteria {

    private DateTime fromDate;
    private DateTime toDate;
    private String path;
    private String fileName;
    private DateTime timestamp;
    private String placePath;
    private String sortByField;
    private Boolean sortDescending;
    private Double latitude;
    private Double longitude;
    private String distance;

    private PageRequest pager = null;

    public SourceCriteria(){
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

    public void setSize(int size) {
        pager = new PageRequest(pager.getPageNumber(), size);
    }

    public int getOffset() {
        return pager.getPageNumber() * pager.getPageSize();
    }

}

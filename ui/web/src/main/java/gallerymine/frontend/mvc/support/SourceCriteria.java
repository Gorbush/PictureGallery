package gallerymine.frontend.mvc.support;

import org.joda.time.DateTime;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.data.domain.PageRequest;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
public class SourceCriteria {

    private DateTime fromDate;

    private DateTime toDate;

    private String path;

    private String fileName;

    private DateTime timestamp;

    private String placePath;

    private String sortByField;

    private Boolean sortDescending;

    private PageRequest pager = null;

    public SourceCriteria(){
        pager = new PageRequest(0,5);
    }

    public DateTime getFromDate() {
        return fromDate;
    }

    public void setFromDate(DateTime fromDate) {
        this.fromDate = fromDate;
    }

    public DateTime getToDate() {
        return toDate;
    }

    public void setToDate(DateTime toDate) {
        this.toDate = toDate;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPlacePath() {
        return placePath;
    }

    public void setPlacePath(String placePath) {
        this.placePath = placePath;
    }

    public String getSortByField() {
        return sortByField;
    }

    public void setSortByField(String sortByField) {
        this.sortByField = sortByField;
    }

    public Boolean getSortDescending() {
        return sortDescending;
    }

    public void setSortDescending(Boolean sortDescending) {
        this.sortDescending = sortDescending;
    }

    public PageRequest getPager() {
        return pager;
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

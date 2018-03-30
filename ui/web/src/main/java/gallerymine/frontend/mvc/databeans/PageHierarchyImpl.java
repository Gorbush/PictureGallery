package gallerymine.frontend.mvc.databeans;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public class PageHierarchyImpl<T> extends PageImpl<T> {

    String root;

    public PageHierarchyImpl(List content, Pageable pageable, long total) {
        this(content, pageable, total, null);
    }


    public PageHierarchyImpl(List content, Pageable pageable, long total, String root) {
        super(content, pageable, total);
        this.root = root;
    }

    public PageHierarchyImpl(List content) {
        super(content);
    }

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }
}

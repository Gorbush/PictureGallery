package gallerymine.model.support;

import lombok.Data;
import gallerymine.model.Process;

import java.util.ArrayList;
import java.util.Collection;

@Data
public class ProcessDetails {

    Process process;
    Collection details;

    public Object getLastDetail() {
        return (details != null && details.size() > 0 )? details.iterator().next() : null;
    }

    public void setDetail(Object detail) {
        details = new ArrayList();
        details.add(detail);
    }
}

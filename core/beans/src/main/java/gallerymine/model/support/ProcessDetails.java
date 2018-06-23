package gallerymine.model.support;

import gallerymine.model.importer.ImportRequest;
import lombok.Data;
import gallerymine.model.Process;

import java.util.ArrayList;
import java.util.Collection;

@Data
public class ProcessDetails {

    Process process;
    Collection<ImportRequest> details;

    public ImportRequest getLastDetail() {
        return (details != null && details.size() > 0 )? details.iterator().next() : null;
    }

    public void setDetail(ImportRequest detail) {
        details = new ArrayList();
        details.add(detail);
    }
}

package gallerymine.model.support;

import gallerymine.model.Source;
import lombok.Data;
import org.apache.commons.collections4.map.HashedMap;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * Source match report to other sources and approved pictures
 * Created by sergii_puliaiev on 6/20/17.
 */
@Data
public class SourceMatchReport {
    
    Map<String, List<String>> pictures = new HashedMap<>();

    Map<String, List<String>> currentImport = new HashedMap<>();

    public static class SourceComparator implements Comparator<Source> {

        @Override
        public int compare(Source o1, Source o2) {
            int result = StringUtils.compare(o1.getFilePath(), o2.getFilePath());
            if (result == 0) {
                result = StringUtils.compare(o1.getFileName(), o2.getFileName());
            }
            if (result == 0) {
                result = Boolean.compare(o1.isExists(), o2.isExists());
            }
            return result;
        }
    }

    public Collection<String> getPicturesKind(String kind) {
        return pictures.computeIfAbsent(kind, k -> new ArrayList<>());
    }

    public Collection<String> getCurrentImportKind(String kind) {
        return currentImport.computeIfAbsent(kind, k -> new ArrayList<>());
    }

}

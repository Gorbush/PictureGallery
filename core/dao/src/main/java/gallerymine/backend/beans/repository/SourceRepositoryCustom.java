package gallerymine.backend.beans.repository;

import gallerymine.model.Source;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.SourceFolderStats;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public interface SourceRepositoryCustom {
    Page<Source> fetchCustom(SourceCriteria criteria);
    Page<FolderStats> fetchPathCustom(SourceCriteria criteria);
    List<DateStats> fetchDatesCustom(SourceCriteria criteria);

    SourceFolderStats getFolderStats(String rootFolder, String folderPath);
//    @Query(value="{'name': ?0}");
//    Page<Foo> findByMethod(String name, Pageable pageable);
}

package gallerymine.backend.beans.repository;

import gallerymine.model.FileInformation;
import gallerymine.model.ImportSource;
import gallerymine.model.PictureInformation;
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
public interface ImportSourceRepositoryCustom {
    <T extends PictureInformation> Page<T> fetchCustom(SourceCriteria criteria, Class<T> clazz);

    <T extends PictureInformation> T fetchOne(String id, Class<T> clazz);
    <T extends PictureInformation> T saveByGrade(T entity);

    Page<FolderStats> fetchPathCustom(SourceCriteria criteria, Class<? extends FileInformation> clazz);
    List<DateStats> fetchDatesCustom(SourceCriteria criteria, Class<? extends FileInformation> clazz);

    SourceFolderStats getFolderStats(String rootFolder, String folderPath, Class<? extends FileInformation> clazz);
//    @Query(value="{'name': ?0}");
//    Page<Foo> findByMethod(String name, Pageable pageable);
}

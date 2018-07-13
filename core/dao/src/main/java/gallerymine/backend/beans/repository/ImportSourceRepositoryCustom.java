package gallerymine.backend.beans.repository;

import gallerymine.model.FileInformation;
import gallerymine.model.PictureInformation;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.ProcessType;
import gallerymine.model.support.SourceFolderStats;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Iterator;
import java.util.List;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public interface ImportSourceRepositoryCustom {
    <T extends PictureInformation> Page<T> fetchCustom(SourceCriteria criteria, Class<T> clazz);

    <T extends PictureInformation> Iterator<T> fetchCustomStream(SourceCriteria criteria, Class<T> clazz);

    <T extends PictureInformation> T fetchOne(String id, Class<T> clazz);
    <T extends PictureInformation> T saveByGrade(T entity);
    <T extends PictureInformation> boolean deleteByGrade(String id, Class<T> clazz);

    <T extends PictureInformation> T findInfo(String id);

    Page<FolderStats> fetchPathCustom(SourceCriteria criteria, Class<? extends FileInformation> clazz);
    List<DateStats> fetchDatesCustom(SourceCriteria criteria, Class<? extends FileInformation> clazz);

    SourceFolderStats getFolderStats(String rootFolder, String folderPath, Class<? extends FileInformation> clazz);

    void updateAllRequestsToNextProcess(String oldProcessId, String newProcessId, ImportRequest.ImportStatus oldStatus, ImportRequest.ImportStatus newStatus, ProcessType processType);
//    @Query(value="{'name': ?0}");
//    Page<Foo> findByMethod(String name, Pageable pageable);
}

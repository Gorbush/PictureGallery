package gallerymine.backend.beans.repository;

import gallerymine.model.PictureInformation;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.ProcessType;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public interface ImportSourceRepositoryCustom<Information extends PictureInformation>  extends FileRepositoryCustom<Information, SourceCriteria>{

    Information saveByGrade(Information entity);

    List<PictureInformation> findInfo(String id);

    void updateAllRequestsToNextProcess(String oldProcessId, String newProcessId, ImportRequest.ImportStatus oldStatus, ImportRequest.ImportStatus newStatus, ProcessType processType);
//    @Query(value="{'name': ?0}");
//    Page<Foo> findByMethod(String name, Pageable pageable);
}

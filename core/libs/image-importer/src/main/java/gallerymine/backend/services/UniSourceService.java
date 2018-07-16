package gallerymine.backend.services;

import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.data.RetryRunner;
import gallerymine.backend.data.RetryVersion;
import gallerymine.model.PictureInformation;
import gallerymine.model.support.InfoStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UniSourceService {

    private static Logger log = LoggerFactory.getLogger(ImportRequestService.class);

    @Autowired
    private ImportSourceRepository uniSourceRepository;

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public <T extends PictureInformation> T saveByGrade(T entity) {
        return uniSourceRepository.saveByGrade(entity);
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public <T extends PictureInformation> T retrySave(String entityId,  Class<T> clazz, RetryRunner<T> runner) {
        T entity = uniSourceRepository.fetchOne(entityId, clazz);
        entity = runner.run(entity);
        if (entity != null) {
            uniSourceRepository.saveByGrade(entity);
        }
        return entity;
    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public <T extends PictureInformation> void addPopulatedBy(String entityId, Class<T> clazz, String kind) {
        PictureInformation entity = uniSourceRepository.fetchOne(entityId, clazz);
        if (entity != null) {
            entity.getPopulatedBy().add(kind);
            uniSourceRepository.saveByGrade(entity);
        }

    }

    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public <T extends PictureInformation> T updateStatus(String requestId, Class<T> clazz, InfoStatus newStatus) {
        T entity = uniSourceRepository.fetchOne(requestId, clazz);
        InfoStatus status = entity.getStatus();
        if (!status.equals(newStatus)) {
            entity.setStatus(newStatus);
            uniSourceRepository.saveByGrade(entity);
            log.info(" Picture status changed old={} path={}", status, entity.getFileName());
        }
        return entity;
    }
}

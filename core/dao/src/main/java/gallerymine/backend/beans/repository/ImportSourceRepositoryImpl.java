package gallerymine.backend.beans.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.WriteResult;
import gallerymine.model.ImportSource;
import gallerymine.model.PictureInformation;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.format.DistanceFormatter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public class ImportSourceRepositoryImpl<Information extends PictureInformation>
        extends FileRepositoryImpl<Information, SourceCriteria>
        implements ImportSourceRepositoryCustom<Information> {

    private static Logger log = LoggerFactory.getLogger(ImportSourceRepositoryImpl.class);

    @Autowired
    private ObjectMapper jacksonObjectMapper;

    @Override
    public List<PictureInformation> findInfo(String id) {
        List<PictureInformation> infos = new ArrayList<>();
        PictureInformation info = fetchOne(id, PictureGrade.GALLERY.getEntityClass());
        if (info != null) {
            infos.add(info);
        }
        info = fetchOne(id, PictureGrade.IMPORT.getEntityClass());
        if (info != null) {
            infos.add(info);
        }
        info = fetchOne(id, PictureGrade.SOURCE.getEntityClass());
        if (info != null) {
            infos.add(info);
        }
        return infos;
    }

    @Override
    public Page<FolderStats> fetchPathCustom(SourceCriteria criteria) {
        PictureGrade grade = criteria.getGrade();
        if (grade == null) {
            grade = PictureGrade.GALLERY;
        }
        return super.fetchPathCustom(criteria, grade.getEntityClass());
    }

    @Override
    public List<DateStats> fetchDatesCustom(SourceCriteria criteria) {
        PictureGrade grade = criteria.getGrade();
        if (grade == null) {
            grade = PictureGrade.GALLERY;
        }
        return super.fetchDatesCustom(criteria, grade.getEntityClass());
    }

    @Override
    public Page<Information> fetchCustom(SourceCriteria criteria) {
        PictureGrade grade = criteria.getGrade();
        if (grade == null) {
            grade = PictureGrade.GALLERY;
        }
        return super.fetchCustom(criteria, grade.getEntityClass());
    }

    @Override
    public Information saveByGrade(Information entity) {
        String collection = entity.getGrade().getCollectionName();
        if (StringUtils.isBlank(collection)) {
            String entityJSON = entity.getId();
            try {
                entityJSON = jacksonObjectMapper.writeValueAsString(entity);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize entity "+entityJSON, e);
            }
            throw new RuntimeException("Wrong entity grade! "+ entityJSON);
        }
        template.save(entity, collection);
        return entity;
    }

    protected List<Criteria> applyCustomCriteria(SourceCriteria searchCriteria) {
        List<Criteria> criteria = super.applyCustomCriteria(searchCriteria);

        if (searchCriteria.getLatitude() != null && searchCriteria.getLongitude() != null &&
                searchCriteria.getDistance() != null) {
            Distance dist = DistanceFormatter.INSTANCE.convert(searchCriteria.getDistance());
            criteria.add(Criteria.where("geoLocation")
                    .near(new Point(searchCriteria.getLatitude(), searchCriteria.getLongitude()))
                    .maxDistance(dist.getNormalizedValue()));
        }

        return criteria;
    }

    @Override
    public void updateAllImportSourcesToNextProcess(String oldProcessId, String newProcessId) {
        Query query = new Query();
        Criteria criteria = Criteria.where("indexProcessIds").is(oldProcessId);
        query.addCriteria(criteria);

        Update update = new Update();
        if (newProcessId != null) {
            update.addToSet("indexProcessIds", newProcessId);
        }

        WriteResult writeResult = template.updateMulti(query, update, ImportSource.class);
        log.info("Updated {} ImportSources id=({} added to {})",
                writeResult.getN(),
                newProcessId, oldProcessId);
    }

    @Override
    public void updateAllRequestsToNextProcess(String oldProcessId, String newProcessId,
                                               ImportRequest.ImportStatus oldStatus, ImportRequest.ImportStatus newStatus,
                                               ProcessType processType) {
        Query query = new Query();
        Criteria criteria = Criteria.where("indexProcessIds").is(oldProcessId);
        if (oldStatus != null) {
            criteria = criteria.andOperator(Criteria.where("status").is(oldStatus));
        }
        query.addCriteria(criteria);

        Update update = new Update();
        update.set("status", newStatus);
        if (processType != null) {
            update.set("activeProcessType", processType);
        }
        if (newProcessId != null) {
            update.addToSet("indexProcessIds", newProcessId);
            update.set("activeProcessId", newProcessId);
        }

        WriteResult writeResult = template.updateMulti(query, update, ImportRequest.class);
        log.info("Updated {} ImportRequests id=({} added to {}) Status=({} -> {})",
                writeResult.getN(),
                newProcessId, oldProcessId,
                oldStatus, newStatus);
    }
}

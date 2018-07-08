package gallerymine.backend.beans.repository;

import gallerymine.backend.utils.RegExpHelper;
import gallerymine.model.FileInformation;
import gallerymine.model.Source;
import gallerymine.model.mvc.FileCriteria;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.PageHierarchyImpl;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.SourceFolderStats;
import gallerymine.model.support.SourceKind;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.geo.format.DistanceFormatter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public class FileRepositoryImpl<RequestCriteria extends FileCriteria, Information extends FileInformation> implements FileRepositoryCustom<RequestCriteria, Information> {

    @Autowired
    MongoTemplate template = null;

    protected Class entityClass;

    public FileRepositoryImpl() {
        entityClass = (Class) getClass().getTypeParameters()[1].getBounds()[0];
    }

    @Override
    public Page<Information> fetchCustom(RequestCriteria fileCriteria) {

        Criteria criteria = applyCustomCriteria(fileCriteria);

        Query query = criteria != null ? Query.query(criteria) : new Query();
        query.skip(fileCriteria.getOffset())
             .limit(fileCriteria.getSize());
        if (StringUtils.isNotBlank(fileCriteria.getSortByField())) {
            Sort.Direction direction = (fileCriteria.getSortDescending()!=null && fileCriteria.getSortDescending()) ?
                    Sort.Direction.DESC : ASC;
            query.with(new Sort(direction, fileCriteria.getSortByField()));
        }

        long count = template.count(query, Source.class);

        Iterator<Information> sources = template.stream(query, entityClass);

        List<Information> sourcesList = new ArrayList<>();
        sources.forEachRemaining( sourcesList::add );

        return new PageImpl<Information>(sourcesList, fileCriteria.getPager(), count);
    }

    private Criteria applyCustomCriteria(FileCriteria fileCriteria) {
        List<Criteria> criteria = new ArrayList<>();

        if (isNotBlank(fileCriteria.getFileName())) {
            if (RegExpHelper.isMask(fileCriteria.getFileName())) {
                criteria.add(Criteria.where("fileName").regex(RegExpHelper.convertToRegExp(fileCriteria.getFileName())));
            } else {
                criteria.add(Criteria.where("fileName").is(fileCriteria.getFileName()));
            }
        }
        if (fileCriteria.getFileSize() != null) {
            criteria.add(Criteria.where("size").is(fileCriteria.getFileSize()));
        }
        if (isNotBlank(fileCriteria.getPath())) {
            if (RegExpHelper.isMask(fileCriteria.getPath())) {
                criteria.add(Criteria.where("filePath").regex(RegExpHelper.convertToRegExp(fileCriteria.getPath())));
            } else {
                criteria.add(Criteria.where("filePath").is(fileCriteria.getPath()));
            }
        }
        if (fileCriteria.getTimestamp() != null) {
            criteria.add(Criteria.where("timestamp").is(fileCriteria.getTimestamp().toDate()));
        } else {
            if (fileCriteria.getTimestamps() != null) {
                List<Date> dates = fileCriteria.getTimestamps().stream().map(AbstractInstant::toDate).collect(Collectors.toList());
                criteria.add(Criteria.where("timestamps.stamp").in(dates));
            } else {
                DateTime starting = fileCriteria.getFromDate();
                DateTime ending = fileCriteria.getToDate();
                if (ending != null && ending.getHourOfDay() == 0 && ending.getMinuteOfHour() == 0) {
                    ending = ending.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
                }
                if (starting != null && ending != null) {
                    criteria.add(Criteria.where("timestamp").gte(starting.toDate()).lte(ending.toDate()));
                } else {
                    if (starting != null) {
                        criteria.add(Criteria.where("timestamp").gte(starting.toDate()));
                    }
                    if (ending != null) {
                        criteria.add(Criteria.where("timestamp").lte(ending.toDate()));
                    }
                }
            }
        }

        if (criteria.size() > 0) {
            return new Criteria().andOperator(criteria.toArray(new Criteria[0]));
        } else {
            return null;
        }
    }

}

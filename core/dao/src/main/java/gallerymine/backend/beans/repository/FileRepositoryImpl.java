package gallerymine.backend.beans.repository;

import com.mongodb.WriteResult;
import gallerymine.backend.utils.RegExpHelper;
import gallerymine.model.FileInformation;
import gallerymine.model.PictureInformation;
import gallerymine.model.mvc.FileCriteria;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.PageHierarchyImpl;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.InfoStatus;
import gallerymine.model.support.SourceFolderStats;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.base.AbstractInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public class FileRepositoryImpl<Information extends FileInformation, RequestCriteria extends FileCriteria> implements FileRepositoryCustom<Information, RequestCriteria> {

    private static Logger log = LoggerFactory.getLogger(FileRepositoryImpl.class);

    @Autowired
    protected MongoTemplate template = null;

    protected Class<Information> entityClass;

    public FileRepositoryImpl() {
        Class clazz = getClass();
        while(clazz != null && clazz.getTypeParameters().length < 1) {
            clazz = clazz.getSuperclass();
        }
        if (clazz != null) {
            entityClass = (Class) clazz.getTypeParameters()[0].getBounds()[0];
        } else {
            throw new RuntimeException("Failed to set up repository - base entity not found for "+this.getClass().getSimpleName());
        }
    }

    @Override
    public Page<Information> fetchCustom(RequestCriteria searchCriteria) {
        return fetchCustom(searchCriteria, entityClass);
    }

    @Override
    public <T extends Information> Page<T> fetchCustom(RequestCriteria searchCriteria, Class<T> clazz) {
        if (clazz == null ) {
            clazz = (Class<T>)entityClass;
        }
        List<Criteria> criteriaList = applyCustomCriteria(searchCriteria);
        Criteria criteria = criteriaList.size() == 0 ? null : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        Query query = criteria != null ? Query.query(criteria) : new Query();
        query.skip(searchCriteria.getOffset())
             .limit(searchCriteria.getSize());
        if (StringUtils.isNotBlank(searchCriteria.getSortByField())) {
            Sort.Direction direction = (searchCriteria.getSortDescending()!=null && searchCriteria.getSortDescending()) ?
                    Sort.Direction.DESC : ASC;
            query.with(new Sort(direction, searchCriteria.getSortByField()));
        }

        long count = template.count(query, clazz);

        Iterator<T> sources = template.stream(query, clazz);

        List<T> sourcesList = new ArrayList<>();
        sources.forEachRemaining( sourcesList::add );

        return new PageImpl<T>(sourcesList, searchCriteria.getPager(), count);
    }

    @Override
    public <T extends Information> Iterator<T> fetchCustomStream(RequestCriteria searchCriteria, Class<T> clazz) {

        List<Criteria> criteriaList = applyCustomCriteria(searchCriteria);
        Criteria criteria = criteriaList.size() == 0 ? null : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        Query query = criteria != null ? Query.query(criteria) : new Query();

        query.skip(searchCriteria.getOffset())
                .limit(searchCriteria.getSize());
        if (StringUtils.isNotBlank(searchCriteria.getSortByField())) {
            Sort.Direction direction = (searchCriteria.getSortDescending()!=null && searchCriteria.getSortDescending()) ?
                    Sort.Direction.DESC : ASC;
            query.with(new Sort(direction, searchCriteria.getSortByField()));
        }
        return template.stream(query, clazz);
    }

    protected List<Criteria> applyCustomCriteria(RequestCriteria searchCriteria) {
        List<Criteria> criteria = new ArrayList<>();

        if (isNotBlank(searchCriteria.getFileName())) {
            Criteria byFileName;
            if (RegExpHelper.isMask(searchCriteria.getFileName())) {
                byFileName = Criteria.where("fileName").regex(RegExpHelper.convertToRegExp(searchCriteria.getFileName()));
            } else {
                byFileName = Criteria.where("fileName").is(searchCriteria.getFileName());
            }
            Criteria byOrgFileName;
            if (RegExpHelper.isMask(searchCriteria.getFileName())) {
                byOrgFileName = Criteria.where("fileNameOriginal").regex(RegExpHelper.convertToRegExp(searchCriteria.getFileName()));
            } else {
                byOrgFileName = Criteria.where("fileNameOriginal").is(searchCriteria.getFileName());
            }
            criteria.add(new Criteria().orOperator(
                    byFileName,
                    byOrgFileName
            ));
        }
        if (searchCriteria.getFileSize() != null) {
            criteria.add(Criteria.where("size").is(searchCriteria.getFileSize()));
        }
        if (isNotBlank(searchCriteria.getPath())) {
            if (RegExpHelper.isMask(searchCriteria.getPath())) {
                criteria.add(Criteria.where("filePath").regex(RegExpHelper.convertToRegExp(searchCriteria.getPath())));
            } else {
                criteria.add(Criteria.where("filePath").is(searchCriteria.getPath()));
            }
        }

        if (isNotBlank(searchCriteria.getRequestId())) {
            criteria.add(Criteria.where("importRequestId").is(searchCriteria.getRequestId()));
        }

        if (searchCriteria.getStatus() != null) {
            criteria.add(Criteria.where("status").is(searchCriteria.getStatus()));
        }

        if (isNotBlank(searchCriteria.getRequestRootId())) {
            criteria.add(Criteria.where("importRequestRootId").is(searchCriteria.getRequestRootId()));
        }

        if (isNotBlank(searchCriteria.getProcessId())) {
            criteria.add(Criteria.where("indexProcessIds").is(searchCriteria.getProcessId()));
        }

        if (searchCriteria.getTimestamp() != null) {
            criteria.add(Criteria.where("timestamp").is(searchCriteria.getTimestamp().toDate()));
        } else {
            if (searchCriteria.getTimestamps() != null) {
                List<Date> dates = searchCriteria.getTimestamps().stream().map(AbstractInstant::toDate).collect(Collectors.toList());
                criteria.add(Criteria.where("timestamps.stamp").in(dates));
            } else {
                DateTime starting = searchCriteria.getFromDate();
                DateTime ending = searchCriteria.getToDate();
                if (ending != null && ending.getHourOfDay() == 0 && ending.getMinuteOfHour() == 0) {
                    ending = ending.withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
                }
                if (starting != null && ending != null) {
                    criteria.add(Criteria.where("timestamp").gte(starting.toDate()).lte(ending.toDate()));
                criteria.add(
                        Criteria.where("timestamps").elemMatch(
                                Criteria.where("stamp").gte(starting.toDate()).lte(ending.toDate())
                        )
                );
                } else {
                    if (starting != null) {
                        criteria.add(Criteria.where("timestamp").gte(starting.toDate()));
                    criteria.add(
                            Criteria.where("timestamps").elemMatch(
                                    Criteria.where("stamp").gte(starting.toDate())
                            )
                    );
                    }
                    if (ending != null) {
                        criteria.add(Criteria.where("timestamp").lte(ending.toDate()));
                    criteria.add(
                            Criteria.where("timestamps").elemMatch(
                                    Criteria.where("stamp").lte(ending.toDate())
                            )
                    );
                }
                    }
                }
            }

        if (searchCriteria.getPopulatedBy() != null) {
            criteria.add(Criteria.where("populatedBy").all(searchCriteria.getPopulatedBy()));
        }

        if (searchCriteria.getPopulatedNotBy() != null) {
            criteria.add(Criteria.where("populatedBy").not().all(searchCriteria.getPopulatedNotBy()));
        }

        return criteria;
    }

    @Override
    public <T extends Information> Page<FolderStats> fetchPathCustom(RequestCriteria searchCriteria, Class<T> clazz) {
        searchCriteria.setSortByField("filePath");
        searchCriteria.setSortDescending(false);
        String sourcePath = searchCriteria.getPath();
        // fixing path - to get all sub-folders - if they have photos
        searchCriteria.setPath(sourcePath+"*");

        List<Criteria> criteriaList = applyCustomCriteria(searchCriteria);
        Criteria criteria = criteriaList == null ? null : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        
        List<AggregationOperation> pipeline = new ArrayList<>();
        List<AggregationOperation> pipelineCount = new ArrayList<>();

        if (criteria != null) {
            pipeline.add(Aggregation.match(criteria));
        }

        int selectLevel = StringUtils.countMatches(sourcePath,"/");

        pipeline.add(project()
                .and("$filePath").as("filePath")
                .and(ArrayOperators.arrayOf(StringOperators.Split.valueOf("$filePath").split("/")).elementAt(selectLevel)).as("name")
        );
        pipeline.add(group(fields("name").and("filePath")).count().as("count"));

        pipelineCount.addAll(pipeline);
        // group them as one line
        pipelineCount.add(group(fields()).sum("count").as("count"));
        pipelineCount.add(project().and("$_id.name").as("name").and("$count").as("filesCount").and("$_id.filePath").as("fullPath"));

        Aggregation aggregationCount  = newAggregation(clazz, (AggregationOperation[]) pipelineCount.toArray(new AggregationOperation[]{}));
//        List<FolderStats> countStatse = template.aggregate(aggregationCount, Source.class, FolderStats.class).getMappedResults();
        FolderStats countStats = template.aggregate(aggregationCount, clazz, FolderStats.class).getUniqueMappedResult();

        long totalCount = (countStats == null || countStats.getFilesCount() == null) ? 0 : countStats.getFilesCount();
        int newOffset = searchCriteria.getOffset();
        int newPage = searchCriteria.getPage();
        if (searchCriteria.getOffset() > totalCount) {
            // offset is over the total list length - get it one page back from the end
            newOffset = (int) (totalCount % searchCriteria.getSize());
            newPage = (int) (totalCount / searchCriteria.getSize());
        }
        PageRequest pager = new PageRequest(newPage, searchCriteria.getSize());

        pipeline.add(project().and("$_id.name").as("name").and("$count").as("filesCount").and("$_id.filePath").as("fullPath"));

        pipeline.add(sort(new Sort(Sort.Direction.DESC, "name")));
        pipeline.add(Aggregation.skip(newOffset));
        pipeline.add(Aggregation.limit(searchCriteria.getSize()));

        Aggregation aggregation  = newAggregation(clazz, (AggregationOperation[]) pipeline.toArray(new AggregationOperation[]{}));

        // get distinct path, but before we need to cut the original path - and everything starting from first slash /
        AggregationResults<FolderStats> output = template.aggregate(aggregation, clazz, FolderStats.class);

        return new PageHierarchyImpl<>(output.getMappedResults(), pager, totalCount, sourcePath);
    }


    @Override
    public <T extends Information> List<DateStats> fetchDatesCustom(RequestCriteria sourceCriteria, Class<T> clazz) {
        List<AggregationOperation> ops = new ArrayList<>();

        List<Criteria> criteriaList = applyCustomCriteria(sourceCriteria);

        criteriaList.add(Criteria.where("timestamp").exists(true));

        Criteria criteria = criteriaList.size() == 0 ? null : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        ops.add(match(criteria));

        ProjectionOperation projectionBy = project().and("timestamp").extractYear().as("byYear")
                .and("timestamp").extractMonth().as("byMonth")
                .and("timestamp").extractDayOfMonth().as("byDay");
        ops.add(projectionBy);

        GroupOperation groupBy = group("byYear", "byMonth", "byDay").count().as("total");
        ops.add(groupBy);

        ops.add(sort(Sort.Direction.DESC, "byYear", "byMonth", "byDay"));

        Aggregation agg = newAggregation(ops);

        //Convert the aggregation result into a List
        AggregationResults<DateStats> groupResults = template.aggregate(agg, clazz, DateStats.class);

        return groupResults.getMappedResults();
    }

    @Override
    public  <T extends Information> SourceFolderStats getFolderStats(String rootFolder, String folderPath, Class<T> clazz) {
        SourceFolderStats stats = new SourceFolderStats();
        stats.setPath(folderPath);

        Path path = Paths.get(rootFolder, folderPath);
        stats.setExists(path.toFile().exists());

        Query query = Query.query(Criteria.where("filePath").is(folderPath));

        Iterator<? extends FileInformation> sources = template.stream(query, clazz);

        sources.forEachRemaining(source -> {
            Path sourcePath = path.resolveSibling(source.getFileName());
            stats.incFiles();

            boolean fileExists = sourcePath.toFile().exists();
            boolean matched = InfoStatus.APPROVED.equals(source.getStatus());
            if (matched) {
                stats.incFilesMatched();
            } else {
                stats.incFilesNotMatched();
            }
            if (fileExists) {
                if (matched) {
                    stats.incFilesExistingMatched();
                } else {
                    stats.incFilesExistingNotMatched();
                }
            } else {
                stats.incFilesNotExisting();
            }

            if (InfoStatus.DUPLICATE.equals(source.getStatus())) {
                stats.incFilesDuplicates();
            }
        });

        return stats;
    }

    @Override
    public <T extends PictureInformation> T fetchOne(String id, Class<T> clazz) {
        return template.findById(id, clazz);
    }

    @Override
    public <T extends PictureInformation> boolean deleteByGrade(String id, Class<T> clazz) {
        Query query = Query.query(Criteria.where("_id").is(id));
        WriteResult remove = template.remove(query, clazz);
        return remove.getN() == 1;
    }

}

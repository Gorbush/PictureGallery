package gallerymine.backend.beans.repository;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import gallerymine.model.Source;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.PageHierarchyImpl;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.PictureGrade;
import gallerymine.model.support.SourceFolderStats;
import gallerymine.model.support.SourceKind;
import gallerymine.backend.utils.RegExpHelper;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public class SourceRepositoryImpl implements SourceRepositoryCustom {

    @Autowired
    MongoTemplate template = null;

    @Override
    public Page<Source> fetchCustom(SourceCriteria sourceCriteria) {

        Criteria criteria = applyCustomCriteria(sourceCriteria);

        Query query = criteria != null ? Query.query(criteria) : new Query();
        query.skip(sourceCriteria.getOffset())
             .limit(sourceCriteria.getSize());
        if (StringUtils.isNotBlank(sourceCriteria.getSortByField())) {
            Sort.Direction direction = (sourceCriteria.getSortDescending()!=null && sourceCriteria.getSortDescending()) ?
                    Sort.Direction.DESC : ASC;
            query.with(new Sort(direction, sourceCriteria.getSortByField()));
        }

        long count = template.count(query, Source.class);

        Iterator<Source> sources = template.stream(query, Source.class);

        List<Source> sourcesList = new ArrayList<>();
        sources.forEachRemaining( sourcesList::add );

        return new PageImpl<>(sourcesList, sourceCriteria.getPager(), count);
    }

    @Override
    public Page<FolderStats> fetchPathCustom(SourceCriteria sourceCriteria) {
        PictureGrade grade = sourceCriteria.getGrade();
        if (grade == null) {
            grade = PictureGrade.GALLERY;
        }
        Class entityClass = grade.getEntityClass();

        sourceCriteria.setSortByField("filePath");
        sourceCriteria.setSortDescending(false);
        String sourcePath = sourceCriteria.getPath();
        // fixing path - to get all sub-folders - if they have photos
        sourceCriteria.setPath(sourcePath+"*");
        Criteria criteria = applyCustomCriteria(sourceCriteria);

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

        Aggregation aggregationCount  = newAggregation(entityClass, (AggregationOperation[]) pipelineCount.toArray(new AggregationOperation[]{}));
//        List<FolderStats> countStatse = template.aggregate(aggregationCount, Source.class, FolderStats.class).getMappedResults();
        FolderStats countStats = template.aggregate(aggregationCount, entityClass, FolderStats.class).getUniqueMappedResult();

        long totalCount = (countStats == null || countStats.getFilesCount() == null) ? 0 : countStats.getFilesCount();
        int newOffset = sourceCriteria.getOffset();
        int newPage = sourceCriteria.getPage();
        if (sourceCriteria.getOffset() > totalCount) {
            // offset is over the total list length - get it one page back from the end
            newOffset = (int) (totalCount % sourceCriteria.getSize());
            newPage = (int) (totalCount / sourceCriteria.getSize());
        }
        PageRequest pager = new PageRequest(newPage, sourceCriteria.getSize());

        pipeline.add(project().and("$_id.name").as("name").and("$count").as("filesCount").and("$_id.filePath").as("fullPath"));

        pipeline.add(sort(new Sort(Sort.Direction.DESC, "name")));
        pipeline.add(Aggregation.skip(newOffset));
        pipeline.add(Aggregation.limit(sourceCriteria.getSize()));

        Aggregation aggregation  = newAggregation(entityClass, (AggregationOperation[]) pipeline.toArray(new AggregationOperation[]{}));

        // get distinct path, but before we need to cut the original path - and everything starting from first slash /
        AggregationResults<FolderStats> output = template.aggregate(aggregation, entityClass, FolderStats.class);

        return new PageHierarchyImpl<>(output.getMappedResults(), pager, totalCount, sourcePath);
    }


    @Override
    public List<DateStats> fetchDatesCustom(SourceCriteria sourceCriteria) {
        List<AggregationOperation> ops = new ArrayList<>();

        Criteria criteria = applyCustomCriteria(sourceCriteria);
        if (criteria != null) {
            criteria = new Criteria().andOperator(criteria, Criteria.where("timestamp").exists(true));
        } else {
            criteria = Criteria.where("timestamp").exists(true);
        }
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
        AggregationResults<DateStats> groupResults = template.aggregate(agg, Source.class, DateStats.class);

        return groupResults.getMappedResults();
    }

    private Criteria applyCustomCriteria(SourceCriteria sourceCriteria) {
        List<Criteria> criteria = new ArrayList<>();

        if (isNotBlank(sourceCriteria.getFileName())) {
            if (RegExpHelper.isMask(sourceCriteria.getFileName())) {
                criteria.add(Criteria.where("fileName").regex(RegExpHelper.convertToRegExp(sourceCriteria.getFileName())));
            } else {
                criteria.add(Criteria.where("fileName").is(sourceCriteria.getFileName()));
            }
        }
        if (isNotBlank(sourceCriteria.getPath())) {
            if (RegExpHelper.isMask(sourceCriteria.getPath())) {
                criteria.add(Criteria.where("filePath").regex(RegExpHelper.convertToRegExp(sourceCriteria.getPath())));
            } else {
                criteria.add(Criteria.where("filePath").is(sourceCriteria.getPath()));
            }
        }
        if (sourceCriteria.getTimestamp() != null) {
            criteria.add(Criteria.where("timestamp").is(sourceCriteria.getTimestamp().toDate()));
        } else {
            DateTime starting = sourceCriteria.getFromDate();
            DateTime ending = sourceCriteria.getToDate();
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
        if (sourceCriteria.getLatitude() != null && sourceCriteria.getLongitude() != null &&
                sourceCriteria.getDistance() != null) {
            Distance dist = DistanceFormatter.INSTANCE.convert(sourceCriteria.getDistance());
            criteria.add(Criteria.where("geoLocation")
                    .near(new Point(sourceCriteria.getLatitude(), sourceCriteria.getLongitude()))
                    .maxDistance(dist.getNormalizedValue()));
        }

        if (criteria.size() > 0) {
            return new Criteria().andOperator(criteria.toArray(new Criteria[0]));
        } else {
            return null;
        }
    }

    @Override
    public SourceFolderStats getFolderStats(String rootFolder, String folderPath) {
        SourceFolderStats stats = new SourceFolderStats();
        stats.setPath(folderPath);

        Path path = Paths.get(rootFolder, folderPath);
        stats.setExists(path.toFile().exists());

        Query query = Query.query(Criteria.where("filePath").is(folderPath));

        Iterator<Source> sources = template.stream(query, Source.class);

        sources.forEachRemaining(source -> {
            Path sourcePath = path.resolveSibling(source.getFileName());
            stats.incFiles();

            boolean fileExists = sourcePath.toFile().exists();
            boolean matched = SourceKind.PRIMARY.equals(source.getKind());
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

            if (SourceKind.DUPLICATE.equals(source.getKind())) {
                stats.incFilesDuplicates();
            }
        });

        return stats;
    }
}

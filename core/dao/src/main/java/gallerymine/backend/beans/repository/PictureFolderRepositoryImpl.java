package gallerymine.backend.beans.repository;

import gallerymine.backend.data.RetryVersion;
import gallerymine.backend.utils.RegExpHelper;
import gallerymine.model.PictureFolder;
import gallerymine.model.mvc.FileCriteria;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.data.domain.Sort.Direction.ASC;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 8/01/17.
 */
@Repository
public class PictureFolderRepositoryImpl implements PictureFolderRepositoryCustom {

    private static Logger log = LoggerFactory.getLogger(PictureFolderRepositoryImpl.class);

    @Autowired
    protected MongoTemplate template = null;

    @Override
    public long incrementFilesCount(String picFolderId) {
        return changeFilesCount(picFolderId, +1);
    }
    @Override
    public long decrementFilesCount(String picFolderId) {
        return changeFilesCount(picFolderId, -1);
    }

    @Override
    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public long changeFilesCount(String picFolderId, long changeValue) {
        PictureFolder picFolder = template.findById(picFolderId, PictureFolder.class);
        picFolder.setFilesCount(picFolder.getFilesCount()+1);
        template.save(picFolder);
        log.info(" incFiles for folder by={} new={} path={}", changeValue, picFolder.getFilesCount(), picFolder.getFullPath());
        return picFolder.getFilesCount();
    }

    @Override
    public long incrementFoldersCount(String picFolderId) {
        return changeFoldersCount(picFolderId, +1);
    }
    @Override
    public long decrementFoldersCount(String picFolderId) {
        return changeFoldersCount(picFolderId, -1);
    }

    @Override
    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public long changeFoldersCount(String picFolderId, long changeValue) {
        PictureFolder picFolder = template.findById(picFolderId, PictureFolder.class);
        picFolder.setFoldersCount(picFolder.getFoldersCount()+changeValue);
        template.save(picFolder);
        log.info(" incFolders for folder by={} new={} path={}", changeValue, picFolder.getFilesCount(), picFolder.getFullPath());
        return picFolder.getFoldersCount();
    }

    protected List<Criteria> applyCustomCriteria(FileCriteria searchCriteria) {
        List<Criteria> criteria = new ArrayList<>();

        if (isNotBlank(searchCriteria.getFileName())) {
            Criteria byFileName;
            if (RegExpHelper.isMask(searchCriteria.getFileName())) {
                byFileName = Criteria.where("namel").regex(RegExpHelper.convertToRegExp(searchCriteria.getFileName()));
            } else {
                byFileName = Criteria.where("namel").is(searchCriteria.getFileName());
            }
            Criteria byOrgFileName;
            if (RegExpHelper.isMask(searchCriteria.getFileName())) {
                byOrgFileName = Criteria.where("name").regex(RegExpHelper.convertToRegExp(searchCriteria.getFileName()));
            } else {
                byOrgFileName = Criteria.where("name").is(searchCriteria.getFileName());
            }
            criteria.add(new Criteria().orOperator(
                    byFileName,
                    byOrgFileName
            ));
        }
        if (searchCriteria.getPath() != null) {
            if (RegExpHelper.isMask(searchCriteria.getPath())) {
                criteria.add(Criteria.where("fullPath").regex(RegExpHelper.convertToRegExp(searchCriteria.getPath())));
            } else {
                criteria.add(Criteria.where("fullPath").is(searchCriteria.getPath()));
            }
        }

        if (searchCriteria.getFolderId() != null) {
            if (searchCriteria.getFolderId().isEmpty()) {
                PictureFolder rootPicFolder = getRootFolder();
                if (rootPicFolder != null) {
                    criteria.add(Criteria.where("folderId").is(rootPicFolder.getId()));
                } else {
                    log.warn("Request is for root gallery picFolder, but it is not found!");
                    criteria.add(Criteria.where("folderId").is("NOT_FOUND"));
                }
            } else {
                criteria.add(Criteria.where("folderId").is(searchCriteria.getFolderId()));
            }
        }

        return criteria;
    }

    @Override
    public PictureFolder getRootFolder() {
        Criteria criteria = Criteria.where("folderId").is("");
        Query query = criteria != null ? Query.query(criteria) : new Query();
        return template.findOne(query, PictureFolder.class);
    }

    @Override
    public Page<PictureFolder> fetchCustom(FileCriteria searchCriteria) {
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

        long count = template.count(query, PictureFolder.class);

        Iterator<PictureFolder> sources = template.stream(query, PictureFolder.class);

        List<PictureFolder> sourcesList = new ArrayList<>();
        sources.forEachRemaining( sourcesList::add );

        return new PageImpl<PictureFolder>(sourcesList, searchCriteria.getPager(), count);
    }
}

package gallerymine.backend.beans.repository;

import gallerymine.backend.data.RetryVersion;
import gallerymine.model.PictureFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * Source repository with custom methods
 * Created by sergii_puliaiev on 6/19/17.
 */
@Repository
public class PictureFolderRepositoryImpl implements PictureFolderRepositoryCustom {

    private static Logger log = LoggerFactory.getLogger(PictureFolderRepositoryImpl.class);

    @Autowired
    protected MongoTemplate template = null;

    @Override
    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public long incrementFilesCount(String picFolderId) {
        PictureFolder picFolder = template.findById(picFolderId, PictureFolder.class);
        picFolder.setFilesCount(picFolder.getFilesCount()+1);
        template.save(picFolder);
        log.info(" incFiles for folder new={} path={}", picFolder.getFilesCount(), picFolder.getPath());
        return picFolder.getFilesCount();
    }

    @Override
    @RetryVersion(times = 10, on = org.springframework.dao.OptimisticLockingFailureException.class)
    public long incrementFoldersCount(String picFolderId) {
        PictureFolder picFolder = template.findById(picFolderId, PictureFolder.class);
        picFolder.setFoldersCount(picFolder.getFoldersCount()+1);
        template.save(picFolder);
        log.info(" incFolders for folder new={} path={}", picFolder.getFilesCount(), picFolder.getPath());
        return picFolder.getFoldersCount();
    }

}

package gallerymine.backend.beans.repository;

import gallerymine.model.support.PoolableEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.io.Serializable;

/**
 * Created by sergii_puliaiev on 6/22/17.
 */
public interface PoolRepository<Entity extends PoolableEntity, Key extends Serializable> extends MongoRepository<Entity, Key> {

    Page<Entity> findByStatus(Key queued, Pageable pageable);
}

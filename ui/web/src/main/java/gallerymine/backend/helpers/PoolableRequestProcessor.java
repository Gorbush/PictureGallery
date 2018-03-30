package gallerymine.backend.helpers;

import gallerymine.model.support.PoolableEntity;

/**
 * Created by sergii_puliaiev on 6/22/17.
 */
public interface PoolableRequestProcessor<Entity extends PoolableEntity> extends Runnable {
    void setRequest(Entity request);
}

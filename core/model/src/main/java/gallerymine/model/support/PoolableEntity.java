package gallerymine.model.support;

/**
 * Created by sergii_puliaiev on 6/22/17.
 */
public interface PoolableEntity {

    String getId();

    boolean isProcessable();

    void setStatus(String status);

    String getStatus();
}

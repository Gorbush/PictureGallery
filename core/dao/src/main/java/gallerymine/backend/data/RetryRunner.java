package gallerymine.backend.data;

import org.springframework.dao.OptimisticLockingFailureException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface RetryRunner<T> {

    boolean run(T entity) throws OptimisticLockingFailureException;
}

package gallerymine.backend.data;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class OptimisticConcurrencyControlAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(OptimisticConcurrencyControlAspect.class);

    @Around("@annotation(gallerymine.backend.data.RetryVersion)")
    public Object retry(ProceedingJoinPoint pjp) throws Throwable {
        RetryVersion retryAnnotation = getRetryAnnotation(pjp);
        return (retryAnnotation != null) ? proceed(pjp, retryAnnotation) : proceed(pjp);
    }

//    @Pointcut("execution(public * *(..))")// the pointcut expression
//    private void anyOldTransfer() {}

    private Object proceed(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }

    private Object proceed(ProceedingJoinPoint pjp, RetryVersion retryAnnotation) throws Throwable {
        int times = retryAnnotation.times();
        Class<? extends Throwable>[] retryOn = retryAnnotation.on();
        Assert.isTrue(times > 0, "@Retry{times} should be greater than 0!");
        Assert.isTrue(retryOn.length > 0, "@Retry{on} should have at least one Throwable!");
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Proceed with {} retries on {} in {}", times, Arrays.toString(retryOn), pjp.toShortString());
        }
        return tryProceeding(pjp, times, retryOn);
    }

    private Object tryProceeding(ProceedingJoinPoint pjp, int times, Class<? extends Throwable>[] retryOn) throws Throwable {
        try {
            return proceed(pjp);
        } catch (Throwable throwable) {
            if(isRetryThrowable(throwable, retryOn) && times-- > 0) {
                LOGGER.info("Optimistic locking detected, {} remaining retries on {} in {}", times, Arrays.toString(retryOn), pjp.toShortString());
                return tryProceeding(pjp, times, retryOn);
            }
            LOGGER.error("Optimistic locking detected, all retries failed on {} in {}", Arrays.toString(retryOn), pjp.toShortString());
            throw throwable;
        }
    }

    private boolean isRetryThrowable(Throwable throwable, Class<? extends Throwable>[] retryOn) {
        Throwable[] causes = ExceptionUtils.getThrowables(throwable);
        for(Throwable cause : causes) {
            for(Class<? extends Throwable> retryThrowable : retryOn) {
                if(retryThrowable.isAssignableFrom(cause.getClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    private RetryVersion getRetryAnnotation(ProceedingJoinPoint pjp) throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        RetryVersion retryAnnotation = AnnotationUtils.findAnnotation(method, RetryVersion.class);

        if(retryAnnotation != null) {
            return retryAnnotation;
        }

        Class[] argClasses = new Class[pjp.getArgs().length];
        for (int i = 0; i < pjp.getArgs().length; i++) {
            argClasses[i] = pjp.getArgs()[i].getClass();
        }
        method = pjp.getTarget().getClass().getMethod(pjp.getSignature().getName(), argClasses);
        return AnnotationUtils.findAnnotation(method, RetryVersion.class);
    }
}
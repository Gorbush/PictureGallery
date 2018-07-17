package gallerymine.backend.pool;

import gallerymine.backend.beans.repository.PoolRepository;
import gallerymine.model.support.PoolableEntity;
import gallerymine.model.support.PoolableEntityStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Pool manager for IndexRequets and processors
 * Created by sergii_puliaiev on 6/14/17.
 */
public class AbstractPoolManager<Entity extends PoolableEntity> {

    private static Logger log = LoggerFactory.getLogger(AbstractPoolManager.class);

    @Autowired
    private ApplicationContext context;

    private PoolRepository<Entity, String> poolRepository;
    private Class<PoolableRequestProcessor<Entity>> requestProcessorClass;

    private ThreadPoolTaskExecutor pool;

    private String poolName;

    public AbstractPoolManager(String poolName, Class<PoolRepository<Entity, String>> repositoryBeanClass, Class<PoolableRequestProcessor<Entity>> requestProcessorClass) {
        this.poolName = poolName;
        poolRepository = context.getBean(repositoryBeanClass);
        this.requestProcessorClass = requestProcessorClass;

        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName(poolName+"Pool");
        pool.setThreadNamePrefix(poolName+"_");

        pool.initialize();
    }

    public ThreadPoolTaskExecutor getPool() {
        return pool;
    }

    private Entity checkRequest(Entity requestSrc) {
        Entity request = poolRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("{} not found for id={} and path={}", poolName, requestSrc.getId(), requestSrc);
            return null;
        }
        if (!request.isProcessable()) {
            log.info("{} status is not processable id={} status={} path={}", poolName, request.getId(), request.getStatus(), request);
            return null;
        }

        request.setStatus(PoolableEntityStatus.AWAITING);
        log.info("{} status changed id={} status={} path={}", poolName, request.getId(), request.getStatus(), request);
        poolRepository.save(request);

        return request;
    }

    public void executeRequest(Entity requestSrc) {
        Entity request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info("{} status changed id={} status={} path={}", poolName, request.getId(), request.getStatus(), request);
        PoolableRequestProcessor<Entity> requestProcessor = context.getBean(requestProcessorClass);
        requestProcessor.setRequest(request);
        pool.execute(requestProcessor);
//        pool.submit(bean);
        log.info("{} scheduled id={} status={} path={}", poolName, request.getId(), request.getStatus(), request);
    }

    @Scheduled(fixedDelay = 60*1000)
    public void checkForAwaitingRequests() {
        Thread.currentThread().setName("AbstractPoolRunner");
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        log.info("{} check queue size={}", poolName, queued);
        if (queued < 1) {
            Page<Entity> foundRequests = poolRepository.findByStatus(PoolableEntityStatus.QUEUED,
                    new PageRequest(0, 5, new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            log.info("{} FOUND size={}", poolName, foundRequests.getNumber());
            for(Entity request: foundRequests) {
                executeRequest(request);
            }
        }
    }

}

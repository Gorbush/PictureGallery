package gallerymine.backend.pool;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.model.importer.ThumbRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Manages Pool of ThumbRequests and processors
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
public class ThumbRequestPool {

    private static Logger log = LoggerFactory.getLogger(ThumbRequestPool.class);

    private static int IN_MEMORY_POOL_LIMIT = 50;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ThumbRequestRepository requestRepository;

    private ThreadPoolTaskExecutor pool;

    public ThumbRequestPool() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName("ThumbRequestPool");
        pool.setThreadNamePrefix("ThumbRequest_");

        pool.initialize();
    }

    private ThumbRequest checkRequest(ThumbRequest requestSrc) {
        ThumbRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("ThumbRequest not found for id={} and path={}", requestSrc.getId(), requestSrc.getFilePath());
            return null;
        }

        request.setInProgress(true);
        log.info("ThumbRequest set in progress id={} and path={}", requestSrc.getId(), requestSrc.getFilePath());
        requestRepository.save(request);

        return request;
    }

    public void executeRequest(ThumbRequest requestSrc) {
        ThumbRequestProcessor bean = context.getBean(ThumbRequestProcessor.class);

        ThumbRequest request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info(" ThumbRequest request execution id={} and path={}", request.getId(), request.getFilePath());
        if (getInMemoryCount() < IN_MEMORY_POOL_LIMIT) {
            bean.setRequest(request);
            pool.execute(bean);
            log.info("  ThumbRequest scheduled id={} and path={}", request.getId(), request.getFilePath());
        } else {
            log.info("  ThumbRequest schedule skipped - inMemory queue is full. id={} path={}", request.getId(), request.getFilePath());
        }
    }

    public int getInMemoryCount() {
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        return queued;
    }

    @Scheduled(fixedDelay = 10*1000)
    public void checkForAwaitingRequests() {
        Thread.currentThread().setName("ThumbRequestRunner");
        if (appConfig.isDisableThumbs()) {
            return;
        }
        int queued = getInMemoryCount();
        if (queued < 1) { // No elements are in memory queue - check DB
            Page<ThumbRequest> foundRequests = requestRepository.findByInProgress(false,
                    new PageRequest(0, 5, new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            log.info("ThumbRequest check db queue size={} (nothing in memory) ", foundRequests.getNumber());
            for(ThumbRequest request: foundRequests) {
                executeRequest(request);
            }
        } else {
            log.info("ThumbRequest check memory queue size={}", queued);
        }
    }

}

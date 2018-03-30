package gallerymine.backend.helpers;

import gallerymine.backend.beans.repository.ThumbRequestRepository;
import gallerymine.model.ThumbRequest;
import org.apache.commons.lang3.reflect.FieldUtils;
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

import java.util.ArrayList;
import java.util.Collection;

/**
 * Manages Pool of ThumbRequests and processors
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
public class ThumbRequestPool {

    private static Logger log = LoggerFactory.getLogger(ThumbRequestPool.class);

    @Autowired
    private ApplicationContext context;

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
            log.info("ThumbRequest not found for id={} and path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName());
            return null;
        }

        request.setInProgress(true);
        log.info("ThumbRequest status changed id={} and path={}/{}", requestSrc.getId(), requestSrc.getFilePath(), requestSrc.getFileName());
        requestRepository.save(request);

        return request;
    }

    public void executeRequest(ThumbRequest requestSrc) {
        ThumbRequestProcessor bean = context.getBean(ThumbRequestProcessor.class);

        ThumbRequest request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info("ThumbRequest status changed id={} and path={}/{}", request.getId(), request.getFilePath(), request.getFileName());
        bean.setRequest(request);
        pool.execute(bean);
        log.info("ThumbRequest scheduled id={} and path={}/{}", request.getId(), request.getFilePath(), request.getFileName());
    }

    @Scheduled(fixedDelay = 10*1000)
    public void checkForAwaitingRequests() {
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        log.info("ThumbRequest check queue size={}", queued);
        if (queued < 1) { // No elements are in memory queue - check DB
            Page<ThumbRequest> foundRequests = requestRepository.findByInProgress(false,
                    new PageRequest(0, 5, new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            log.info("ThumbRequest FOUND size={}", foundRequests.getNumber());
            for(ThumbRequest request: foundRequests) {
                executeRequest(request);
            }
        }
    }

}

package gallerymine.backend.helpers;

import gallerymine.backend.beans.repository.IndexRequestRepository;
import gallerymine.model.importer.IndexRequest;
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
 * Pool manager for IndexRequets and processors
 * Created by sergii_puliaiev on 6/14/17.
 */
@Component
public class IndexRequestPoolManager {

    private static Logger log = LoggerFactory.getLogger(IndexRequestProcessor.class);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IndexRequestRepository requestRepository;

    private ThreadPoolTaskExecutor pool;

    public IndexRequestPoolManager() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName("IndexRequestPool");
        pool.setThreadNamePrefix("IndexRequest_");

        pool.initialize();

    }

    public ThreadPoolTaskExecutor getPool() {
        return pool;
    }

    private IndexRequest checkRequest(IndexRequest requestSrc) {
        IndexRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("IndexRequest not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!request.isProcessable()) {
            log.info("IndexRequest status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        request.setStatus(IndexRequest.IndexStatus.AWAITING);
        log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        requestRepository.save(request);

        return request;
    }

    public void executeRequest(IndexRequest requestSrc) {
        IndexRequestProcessor bean = context.getBean(IndexRequestProcessor.class);

        IndexRequest request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info("IndexRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        bean.setRequest(request);
        pool.execute(bean);
//        pool.submit(bean);
        log.info("IndexRequest scheduled id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

    public Collection<IndexRequestProcessor> getWorkingProcessors() {
        Collection<IndexRequestProcessor> list = new ArrayList<>();
        try {
            Collection<Object> workersFieldUtils = (Collection<Object>) FieldUtils.readField(pool.getThreadPoolExecutor(), "workers", true);
            for(Object worker: workersFieldUtils) {
                Runnable task = (Runnable) FieldUtils.readField(worker, "firstTask", true);
                list.add((IndexRequestProcessor) task);
            }
        } catch (IllegalAccessException e) {
            log.error("Failed to read workers field from pool");
        }
        return list;
    }

    @Scheduled(fixedDelay = 60*1000)
    public void checkForAwaitingRequests() {
        Thread.currentThread().setName("IndexRequestRunner");
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        log.info("IndexRequest check queue size={}", queued);
        if (queued < 1) { // No elements are in memory queue - check DB
            Page<IndexRequest> foundRequests = requestRepository.findByStatus(IndexRequest.IndexStatus.FOUND,
                    new PageRequest(0, 5, new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            log.info("IndexRequest FOUND size={}", foundRequests.getNumber());
            for(IndexRequest request: foundRequests) {
                executeRequest(request);
            }
        }
    }

}

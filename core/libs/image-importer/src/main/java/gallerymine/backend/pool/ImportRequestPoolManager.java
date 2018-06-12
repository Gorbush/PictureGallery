package gallerymine.backend.pool;

import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.IndexRequestRepository;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.model.importer.ImportRequest;
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

import static gallerymine.model.importer.ImportRequest.ImportStatus.*;

/**
 * Pool manager for ImportRequests and processors
 * Created by sergii_puliaiev on 6/14/18.
 */
@Component
public class ImportRequestPoolManager {

    private static Logger log = LoggerFactory.getLogger(ImportRequestPoolManager.class);
    private static Logger logPools = LoggerFactory.getLogger("pools");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ImportRequestRepository requestRepository;

    private ThreadPoolTaskExecutor pool;

    public ImportRequestPoolManager() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName("ImportRequestPool");
        pool.setThreadNamePrefix("ImportRequest_");

        pool.initialize();

    }

    public ThreadPoolTaskExecutor getPool() {
        return pool;
    }

    private ImportRequest checkRequest(ImportRequest requestSrc) {
        ImportRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info("ImportRequest not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!request.isProcessable()) {
            log.info("ImportRequest status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        request.setStatus(AWAITING);
        log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        requestRepository.save(request);

        return request;
    }

    public void executeRequest(ImportRequest requestSrc) {
        ImportProcessor bean = context.getBean(ImportProcessor.class);

        ImportRequest request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info("ImportRequest status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        bean.setRequest(request);
        bean.setPool(this);
        pool.execute(bean);
//        pool.submit(bean);
        log.info("ImportRequest scheduled id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
    }

    public Collection<ImportProcessor> getWorkingProcessors() {
        Collection<ImportProcessor> list = new ArrayList<>();
        try {
            Collection<Object> workersFieldUtils = (Collection<Object>) FieldUtils.readField(pool.getThreadPoolExecutor(), "workers", true);
            for(Object worker: workersFieldUtils) {
                Runnable task = (Runnable) FieldUtils.readField(worker, "firstTask", true);
                list.add((ImportProcessor) task);
            }
        } catch (IllegalAccessException e) {
            log.error("Failed to read workers field from pool");
        }
        return list;
    }

    @Scheduled(fixedDelay = 60*1000)
    public void checkForAwaitingRequests() {
        Thread.currentThread().setName("ImportRequestRunner");
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        logPools.info("ImportRequest check queue size={}", queued);
        if (queued < 1) { // No elements are in memory queue - check DB
            Page<ImportRequest> foundRequests = requestRepository.findByStatus(START,
                    new PageRequest(0, 5, new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            logPools.info("ImportRequest FOUND size={}", foundRequests.getNumber());
            for(ImportRequest request: foundRequests) {
                executeRequest(request);
            }
        }
    }

}
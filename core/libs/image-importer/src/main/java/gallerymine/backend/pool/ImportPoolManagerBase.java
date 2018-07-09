package gallerymine.backend.pool;

import com.google.common.collect.Sets;
import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.importer.ImportProcessorBase;
import gallerymine.model.Process;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessType;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import static gallerymine.model.importer.ImportRequest.ImportStatus.ABANDONED;

/**
 * Pool manager for ImportRequests and processors
 * Created by sergii_puliaiev on 6/14/18.
 */
public abstract class ImportPoolManagerBase {

    private static Logger log = LoggerFactory.getLogger(ImportPoolManagerBase.class);
    private static Logger logPools = LoggerFactory.getLogger("pools");

    @Autowired
    protected ApplicationContext context;

    @Autowired
    protected ImportRequestRepository requestRepository;

    @Autowired
    protected ProcessRepository processRepository;

    @Autowired
    protected AppConfig appConfig;

    private String beanName;
    private StatusHolder statusHolder;
    private Class<? extends ImportProcessorBase> processorClass;

    protected ThreadPoolTaskExecutor pool;
    protected ProcessType processType;

    @Data
    @Getter
    public static class StatusHolder {

        private Set<ImportRequest.ImportStatus> forProcessing = Sets.newHashSet(ImportRequest.ImportStatus.TO_MATCH);
        private Set<ImportRequest.ImportStatus> abandoned = Sets.newHashSet(ImportRequest.ImportStatus.MATCHING_AWAIT, ImportRequest.ImportStatus.MATCHING,ImportRequest.ImportStatus.MATCHED);
        private ImportRequest.ImportStatus awaitingProcessing = ImportRequest.ImportStatus.MATCHING_AWAIT;
        private ImportRequest.ImportStatus inProcessing = ImportRequest.ImportStatus.MATCHING;
        private ImportRequest.ImportStatus processingDone = ImportRequest.ImportStatus.MATCHED;
        private ImportRequest.ImportStatus finished = ImportRequest.ImportStatus.MATCHING_COMPLETE;

        public static StatusHolder define(ImportRequest.ImportStatus AWAITING_PROCESSING, ImportRequest.ImportStatus IN_PROCESSING, ImportRequest.ImportStatus PROCESSING_DONE, ImportRequest.ImportStatus FINISHED) {
            StatusHolder holder = new StatusHolder();
            holder.awaitingProcessing = AWAITING_PROCESSING;
            holder.inProcessing = IN_PROCESSING;
            holder.processingDone = PROCESSING_DONE;
            holder.finished = FINISHED;
            return holder;
        }

        public StatusHolder processing(ImportRequest.ImportStatus... statuses) {
            this.forProcessing = Sets.newHashSet(statuses);
            return this;
        }

        public StatusHolder abandoned(ImportRequest.ImportStatus... statuses) {
            this.abandoned = Sets.newHashSet(statuses);
            return this;
        }
    }

    public ImportPoolManagerBase(StatusHolder statusHolder, String name, Class<? extends ImportProcessorBase> processorClass,
                                 ProcessType processType) {
        this.beanName = name;
        this.statusHolder = statusHolder;
        this.processorClass = processorClass;
        this.processType = processType;

        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName(beanName+"Pool");
        pool.setThreadNamePrefix(beanName+"_");

        pool.initialize();

    }

    public ThreadPoolTaskExecutor getPool() {
        return pool;
    }

    private ImportRequest checkRequest(ImportRequest requestSrc) {
        ImportRequest request = requestRepository.findOne(requestSrc.getId());
        if (request == null) {
            log.info(beanName+" not found for id={} and path={}", requestSrc.getId(), requestSrc.getPath());
            return null;
        }
        if (!statusHolder.getForProcessing().contains(request.getStatus())) {
            log.info(beanName+" status is not processable id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
            return null;
        }

        return request;
    }

    public void executeRequest(ImportRequest requestSrc) {
        ImportProcessorBase bean = context.getBean(processorClass);

        ImportRequest request = checkRequest(requestSrc);
        if (request == null) {
            return;
        }
        log.info(beanName+" request execution id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());

        request.setStatus(statusHolder.awaitingProcessing);
        log.info(beanName+" status changed id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
        requestRepository.save(request);

        bean.setRequest(request);
        bean.setPool(this);
        pool.execute(bean);

        log.info(beanName+" scheduled id={} status={} path={}", request.getId(), request.getStatus(), request.getPath());
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
            log.error(beanName+" Failed to read workers field from pool");
        }
        return list;
    }

    @Scheduled(fixedDelay = 60*1000)
    public void checkForAwaitingRequests() {
        Thread.currentThread().setName(beanName+"-Runner");
        int queued = pool.getThreadPoolExecutor().getQueue().size();
        logPools.info(beanName+" check queue size={}", queued);
        if (queued < 1) { // No elements are in memory queue - check DB
            Page<ImportRequest> foundRequests = requestRepository.findByStatusIn(
                    statusHolder.forProcessing,
                    new PageRequest(0, 5,
                            new Sort(new Sort.Order(Sort.Direction.DESC, "updated"))));
            logPools.info(beanName+" FOUND size={}", foundRequests.getNumber());
            for(ImportRequest request: foundRequests) {
                executeRequest(request);
            }
        }
    }

    @Scheduled(fixedDelay = 3*60*1000)
    public void checkForAbandonedRequests() {
        Thread.currentThread().setName(beanName+"-AbandonedChecker");
        if (statusHolder.abandoned == null || statusHolder.abandoned.size() < 1) {
            logPools.debug(beanName+" Skipped as no statuses marked as abandonable specified");
            return;
        }
        Collection<ImportRequest> foundRequests = requestRepository.findByStatus(statusHolder.abandoned);
        if (!foundRequests.isEmpty()) {
            logPools.info(beanName+" Found potentially abandoned");
            long tested = 0;
            long marked = 0;
            DateTime now = DateTime.now();
            for (ImportRequest request : foundRequests) {
                Process process = processRepository.findByIdInAndTypeIs(request.getIndexProcessIds(), processType);
                if (process == null) {
                    throw new IllegalArgumentException(beanName+" Process for import request not found requestId={}"+request.getId());
                }
                ImportRequest lastUpdated = requestRepository.findLastUpdated(process.getId(), new Sort(new Sort.Order(Sort.Direction.DESC, "updated")));
                if (lastUpdated != null && now.getMillis() - appConfig.getAbandonedTimoutMs() > lastUpdated.getUpdated().getMillis() ) {
                    request.setStatus(ABANDONED);
                    logPools.info(beanName+" Marked abandoned id:%d", request.getId());
                    requestRepository.save(request);
                }
            }
            logPools.info(beanName+" potentially abandoned validation done tested:%d marked:%d", tested, marked);
        } else {
            logPools.info(beanName+" No potentially abandoned found");
        }
    }

}

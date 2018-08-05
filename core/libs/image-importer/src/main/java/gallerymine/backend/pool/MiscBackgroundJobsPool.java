package gallerymine.backend.pool;

import gallerymine.backend.beans.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * Manages Pool of BackgroundJobs
 * Created by sergii_puliaiev on 8/03/18.
 */
@Component
public class MiscBackgroundJobsPool {

    private static Logger log = LoggerFactory.getLogger(MiscBackgroundJobsPool.class);

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AppConfig appConfig;

    private ThreadPoolTaskExecutor pool;

    public MiscBackgroundJobsPool() {
        pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(10);
        pool.setMaxPoolSize(10);
        pool.setWaitForTasksToCompleteOnShutdown(true);

        pool.setThreadGroupName("MiscBackJobPool");
        pool.setThreadNamePrefix("MiscBackJobPool_");

        pool.initialize();
    }

    public void executeRequest(String name, Runnable runnerJob) {
        if (runnerJob == null) {
            return;
        }
        BackgroundJobRunner runner = new BackgroundJobRunner() {
            @Override
            public void run() {
                Thread.currentThread().setName(Thread.currentThread().getName()+"-"+getName());
                try {
                    runnerJob.run();
                } catch (Exception e){
                    log.error("Failed back job with name {}. Reason: {}", getName(), e.getMessage(), e);
                }
            }
        };
        runner.setName(name);
        log.info(" Misc Background Job request execution id={} and path={}", name);
        pool.execute(runner);
        log.info("  Misc Background Job scheduled id={} and path={}", name);
    }

}

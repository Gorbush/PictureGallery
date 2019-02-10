/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gallerymine;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableScheduling
@EnableMongoAuditing
@EnableMongoRepositories
@EnableAspectJAutoProxy(proxyTargetClass = true)
//@EnableLoadTimeWeaving
@Import({ WebAppConfig.class, WebMvcAutoConfiguration.WebMvcAutoConfigurationAdapter.class })
@ComponentScan(basePackages = {
		"gallerymine.backend",
		"gallerymine.frontend.mvc",
		"gallerymine.services.rest"})
public class GalleryMineApplication implements CommandLineRunner {

    @Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
    public TaskExecutor taskExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        return executor;
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        return scheduler;
    }

    // Here you must add converters to Joda datetypes. In my solution is ZonedDateTime
//    @Override
//    public CustomConversions customConversions() {
//        List<Converter<?, ?>> converterList = new ArrayList<>();
//        converterList.add(new DateToZonedDateTimeConverter());
//        converterList.add(new ZonedDateTimeToDateConverter());
//        return new CustomConversions(converterList);
//    }

	@Override
	public void run(String... args) {
	}

	public static void main(String[] args) {
		SpringApplication.run(GalleryMineApplication.class, args);
	}

}

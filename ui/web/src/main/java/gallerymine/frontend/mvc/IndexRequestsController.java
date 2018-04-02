/*
 * Copyright 2012-2016 the original author or authors.
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

package gallerymine.frontend.mvc;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.CustomerRepository;
import gallerymine.backend.beans.repository.IndexRequestRepository;
import gallerymine.backend.helpers.IndexRequestPoolManager;
import gallerymine.backend.helpers.IndexRequestProcessor;
import gallerymine.model.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseWarn;

/**
 * @author Rob Winch
 * @author Doo-Hwan Kwak
 */
@Controller
@RequestMapping("/indexing")
public class IndexRequestsController {

	private static Logger log = LoggerFactory.getLogger(IndexRequestsController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
	private CustomerRepository messageRepository;

	@Autowired
	private IndexRequestRepository indexRequestRepository;

	@Autowired
	private IndexRequestProcessor indexRequestProcessor;

	@Autowired
	private IndexRequestPoolManager indexRequestPool;

	public IndexRequestsController() {
	}

	@GetMapping
    @ResponseBody
	public Object listActive() {
		Collection<IndexRequestProcessor> workingProcessors = indexRequestPool.getWorkingProcessors();

        return responseOk()
            .put("working", workingProcessors)
		    .put("queueSize", indexRequestPool.getPool().getThreadPoolExecutor().getQueue().size())
		    .put("taskCount", indexRequestPool.getPool().getThreadPoolExecutor().getTaskCount())
		    .put("activeCount", indexRequestPool.getPool().getThreadPoolExecutor().getActiveCount())
		    .build();
	}

    @GetMapping("index")
    @ResponseBody
    public Object index(@RequestParam(value = "enforce", defaultValue = "false", required = false) boolean enforce) {
        Path path = Paths.get(appConfig.getSourcesRootFolder());

        String pathToIndex = path.toAbsolutePath().toString();

        IndexRequest request = indexRequestRepository.findByPath(pathToIndex);

        if (!enforce) {
            if (request != null && request.getStatus() != IndexRequest.IndexStatus.DONE) {
                return responseError("Indexing is already in progress").build();
            }
        }
        try {
            request = indexRequestProcessor.registerNewFolderRequest(pathToIndex, null);
            indexRequestProcessor.processRequest(request);

            return responseOk().build();
        } catch (Exception e) {
            return responseError("Failed to index. Reason: "+e.getMessage(), e);
        }
    }

    @GetMapping(value = {"list/", "list/{parentId}"} )
    @ResponseBody
    public Object listByParent(@PathVariable("parentId") Optional<String> parentId) {
        Page<IndexRequest> page = indexRequestRepository.findByParent(
                parentId.isPresent()? parentId.get() : null,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));

        return responseOk().put("response", page).build();
    }

}

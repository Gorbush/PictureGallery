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
import gallerymine.backend.beans.repository.ImportRequestRepository;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.backend.importer.ImportProcessor;
import gallerymine.backend.pool.ImportRequestPoolManager;
import gallerymine.backend.services.ImportService;
import gallerymine.backend.utils.ImportUtils;
import gallerymine.model.importer.ImportRequest;
import gallerymine.model.support.ProcessType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Optional;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;

/**
\ */
@Controller
@RequestMapping("/importing")
public class ImportRequestsController {

	private static Logger log = LoggerFactory.getLogger(ImportRequestsController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
	private CustomerRepository messageRepository;

	@Autowired
	private ImportRequestRepository requestRepository;

	@Autowired
    private ProcessRepository processRepository;

	@Autowired
	private ImportProcessor requestProcessor;

	@Autowired
	private ImportRequestPoolManager requestPool;

	@Autowired
    private ImportUtils importUtils;

	@Autowired
	private ImportService importService;

	public ImportRequestsController() {
	}

	@GetMapping
    @ResponseBody
	public Object listActive() {
		Collection<ImportProcessor> workingProcessors = requestPool.getWorkingProcessors();

        return responseOk()
            .put("working", workingProcessors)
		    .put("queueSize", requestPool.getPool().getThreadPoolExecutor().getQueue().size())
		    .put("taskCount", requestPool.getPool().getThreadPoolExecutor().getTaskCount())
		    .put("activeCount", requestPool.getPool().getThreadPoolExecutor().getActiveCount())
		    .build();
	}

    @GetMapping("import")
    @ResponseBody
    public Object importFolder(@RequestParam(value = "enforce", defaultValue = "false", required = false) boolean enforce) {
	    try {
            ImportRequest request = importService.prepareImportFolder(enforce);

            return responseOk()
                    .put("id", request.getId())
                    .put("op", "import")
                    .put("importFolder", request.getPath())
                    .build();
        } catch (Exception e) {
            return responseError("Failed to index. Reason: "+e.getMessage(), e);
        }
    }

    @GetMapping(value = {"list/{processId}", "list/{processId}/{parentId}"} )
    @ResponseBody
    public Object listByParent(@PathVariable("processId") String processId, @PathVariable("parentId") Optional<String> parentId) {
		log.info("ImportRequests list by parent request={} process={}", parentId.orElseGet(() -> "ROOT"), processId);
        Page<ImportRequest> page = requestRepository.findByParentAndIndexProcessIds(
                parentId.orElse(null),
				processId,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.ASC, "nameL"))));
		long tm = System.currentTimeMillis();
		page.getContent().forEach(p -> {
			p.getStats(ProcessType.APPROVAL).getFailed().set(tm);
			p.setName(p.getName() + tm);
		});

		ImportRequest request = null;
		if (parentId.isPresent()) {
			request = requestRepository.findOne(parentId.get());
			if (request != null) {
				request.getStats(ProcessType.APPROVAL).getFailed().set(tm);
				request.setName(request.getName() + tm);
			} else {
				log.warn("Request not found");
			}
		}

        return responseOk()
				.results(page)
				.result(request)
				.build();
    }

    @GetMapping("{id}" )
    @ResponseBody
    public Object get(@PathVariable("id") String id) {
        ImportRequest request = requestRepository.findOne(id);

        return responseOk()
				.op("get")
				.result(request)
				.build();
    }

}

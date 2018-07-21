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
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseErrorNotFound;
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
        Page<ImportRequest> page;
        if (parentId.isPresent()) {
			page = requestRepository.findByParentAndIndexProcessIds(
					parentId.get(),
					processId,
					new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.ASC, "nameL"))));
		} else {
			page = requestRepository.findSubRootIndexes(processId,
					new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.ASC, "nameL"))));
		}
		ImportRequest request = null;
		if (parentId.isPresent()) {
			request = requestRepository.findOne(parentId.get());
		} else {
			Page<ImportRequest> pageOfRoots = requestRepository.findRootIndexes(processId,
					new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.ASC, "nameL"))));
			if (pageOfRoots != null && pageOfRoots.getTotalElements() > 0) {
				request = pageOfRoots.getContent().get(0);
			} else {
				log.warn("RootRequest not found");
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

	@GetMapping("/approveImport/{importId}")
	@ResponseBody
	public Object approveImport(@PathVariable("importId") String importRequestId,
									  @RequestAttribute("tentativeOnly") Optional<Boolean> tentativeOnlyOption,
									  @RequestAttribute("subFolders") Optional<Boolean> subFoldersOption) {
		boolean tentativeOnly = tentativeOnlyOption.orElse(true);
		boolean subFolders = subFoldersOption.orElse(true);

		ImportRequest request = requestRepository.findOne(importRequestId);
		if (request == null) {
			return responseErrorNotFound("Not found")
					.op("approveImport")
					.put("importId", importRequestId)
					.put("tentativeOnly", tentativeOnly)
					.put("importId", subFolders)
					.build();
		}

		if (
				request.getStatus().equals(ImportRequest.ImportStatus.APPROVING)
				||
				request.getStatus().equals(ImportRequest.ImportStatus.APPROVED)
		) {
			log.info("Approving node requestId={} status={}", request.getId(), request.getStatus());
			importService.approveImportRequest(request, tentativeOnly, subFolders);
		} else {
			log.warn("Wrong status - not APPROVING or APPROVED for requestId={} status={}", request.getId(), request.getStatus());
			return responseError("Wrong status - not APPROVING or APPROVED")
					.op("approveImport")
					.put("importId", importRequestId)
					.put("tentativeOnly", tentativeOnly)
					.put("importId", subFolders)
					.build();
		}

		return responseOk()
				.op("approveImport")
				.put("importId", importRequestId)
				.put("tentativeOnly", tentativeOnly)
				.put("importId", subFolders)
				.build();
	}
	@GetMapping("/rematchImport/{importId}")
	@ResponseBody
	public Object rematchImport(@PathVariable("importId") String importRequestId,
									  @RequestAttribute("tentativeOnly") Optional<Boolean> tentativeOnlyOption,
									  @RequestAttribute("subFolders") Optional<Boolean> subFoldersOption) {
		boolean tentativeOnly = tentativeOnlyOption.orElse(true);
		boolean subFolders = subFoldersOption.orElse(true);

		ImportRequest request = requestRepository.findOne(importRequestId);
		if (request == null) {
			return responseErrorNotFound("Not found")
					.op("rematchImport")
					.put("importId", importRequestId)
					.put("tentativeOnly", tentativeOnly)
					.put("importId", subFolders)
					.build();
		}

		if (
				request.getStatus().equals(ImportRequest.ImportStatus.APPROVING)
				||
				request.getStatus().equals(ImportRequest.ImportStatus.APPROVED)
		) {
			log.info("Re-match node requestId={} status={}", request.getId(), request.getStatus());
			importService.rematchImportRequest(request, tentativeOnly, subFolders);
			return responseOk()
					.op("rematchImport")
					.put("importId", importRequestId)
					.put("tentativeOnly", tentativeOnly)
					.put("importId", subFolders)
					.build();

		} else {
			log.warn("Wrong status - not APPROVING or APPROVED for requestId={} status={}", request.getId(), request.getStatus());
			return responseError("Wrong status - not APPROVING or APPROVED")
					.op("rematchImport")
					.put("importId", importRequestId)
					.put("tentativeOnly", tentativeOnly)
					.put("importId", subFolders)
					.build();
		}

	}
}

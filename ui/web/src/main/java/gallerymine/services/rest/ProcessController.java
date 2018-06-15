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

package gallerymine.services.rest;

import gallerymine.backend.beans.AppConfig;
import gallerymine.backend.beans.repository.ProcessRepository;
import gallerymine.model.Process;
import gallerymine.model.support.ProcessStatus;
import gallerymine.model.support.ProcessType;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseWarn;

/**
 */
@Controller
@RequestMapping("/processes")
public class ProcessController {

	private static Logger log = LoggerFactory.getLogger(ProcessController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
	private ProcessRepository processRepository;

	public ProcessController() {
	}

    @PutMapping("/")
    @ResponseBody
    public Object put(@RequestBody Process process) {
        process.setStarted(DateTime.now());
        process.setStatus(ProcessStatus.STARTING);
        Process processSaved = processRepository.save(process);

        return responseOk()
                .result(processSaved)
                .op("put")
                .original(process)
                .build();
    }

    @PutMapping("/{type}/{name}")
    @ResponseBody
    public Object put(@PathVariable("type") String type, @PathVariable("name") String name) {
        Process process = new Process();
        process.setType(ProcessType.valueOf(type));
        process.setName(name);
	    process.setStarted(DateTime.now());
        process.setStatus(ProcessStatus.STARTING);
        Process processSaved = processRepository.save(process);

        return responseOk()
                .result(processSaved)
                .op("put")
                .original(process)
                .build();
    }

    @PutMapping("/status/{id}/{status}")
    @ResponseBody
    public Object changeStatus(@PathVariable("id") String id, @PathVariable("status") String status) {
        Process process = processRepository.findOne(id);
        if (process == null) {
            return responseError("Process not found")
                    .op("statusChange")
                    .putId(id)
                    .put("status", status)
                    .build();
        }
        ProcessStatus newStatus = ProcessStatus.valueOf(status);
        ProcessStatus oldStatus = process.getStatus();
        if (newStatus.equals(oldStatus)) {
            return responseWarn("Process status is the same")
                    .op("statusChange")
                    .putId(id)
                    .put("status", status)
                    .build();
        }
        process.setStatus(newStatus);
        if (process.getStatus().isFinalStatus()) {
            process.setFinished(DateTime.now());
        }
        Process processSaved = processRepository.save(process);

        return responseOk()
                .result(processSaved)
                .op("statusChange")
                .put("status", newStatus)
                .put("oldStatus", oldStatus)
                .putId(id)
                .build();
    }

    @GetMapping("{id}")
    @ResponseBody
	public Object  find(@PathVariable String id) {
        Process process = processRepository.findOne(id);
		return responseOk()
				.result(process)
				.op("find")
				.putId(id)
				.build();
	}
}

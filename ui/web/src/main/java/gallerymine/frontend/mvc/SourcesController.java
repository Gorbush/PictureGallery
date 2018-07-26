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
import gallerymine.backend.beans.repository.ImportSourceRepository;
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.matchers.SourceFilesMatcher;
import gallerymine.backend.services.ImportService;
import gallerymine.model.Picture;
import gallerymine.model.PictureInformation;
import gallerymine.model.importer.ActionRequest;
import gallerymine.model.Source;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.*;
import gallerymine.frontend.mvc.support.ResponseBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseErrorNotFound;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;

/**
 */
@Controller
@RequestMapping("/sources")
public class SourcesController {

	private static Logger log = LoggerFactory.getLogger(SourcesController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
    private SourceFilesMatcher sourceFilesMatcher;

	@Autowired
    private ImportSourceRepository uniSourceRepository;

	@Autowired
	private ImportService importService;

	public SourcesController() {
	}

    @PostMapping("find")
    @ResponseBody
	public Object list(@RequestBody SourceCriteria criteria) {

		Page<PictureInformation> sources = uniSourceRepository.fetchCustom(criteria);

        return responseOk()
            .put("list", sources)
            .put("op", "find")
            .put("criteria", criteria)
		    .build();
	}

    @GetMapping("approve/{grade}/{id}/{action}" )
    @ResponseBody
    public Object approveAction(@PathVariable("grade") PictureGrade grade, @PathVariable("id") String id, @PathVariable("action") String action) {
        PictureInformation source = uniSourceRepository.fetchOne(id, grade.getEntityClass());

        if (source == null) {
            return responseErrorNotFound("Not found")
                    .put("op", "approve")
                    .put("id", id)
                    .put("grade", grade)
                    .put("action", action)
                    .build();
        }
        Boolean done = false;
        try {
            if ("approve".equals(action)) {
                done = importService.actionApprove(source);
            }
            if ("duplicate".equals(action)) {
                done = importService.actionMarkAsDuplicate(source);
            }

            return responseOk()
                    .result(source)
                    .put("op", "approve")
                    .put("id", id)
                    .put("grade", grade)
                    .put("action", action)
                    .put("done", done)
                    .build();
        } catch (Exception e) {
            return responseError("Failed to approve. Reason: "+e.getMessage(), e)
                    .put("op", "approve")
                    .put("id", id)
                    .put("grade", grade)
                    .put("action", action)
                    .put("done", done);
        }
    }

    @GetMapping("get/{grade}/{id}" )
    @ResponseBody
    public Object getOne(@PathVariable("grade") PictureGrade grade, @PathVariable("id") String id) {
        PictureInformation source = uniSourceRepository.fetchOne(id, grade.getEntityClass());

        if (source == null) {
            return responseErrorNotFound("Not found")
                    .put("op", "get")
                    .put("id", id)
                    .put("grade", grade)
                    .build();
        }
        try {
            return responseOk()
                    .result(source)
                    .put("op", "get")
                    .put("id", id)
                    .put("grade", grade)
                    .build();
        } catch (Exception e) {
            return responseError("Failed to get. Reason: "+e.getMessage(), e)
                    .put("op", "approve")
                    .put("id", id)
                    .put("grade", grade)
                    .build();
        }
    }

    @PostMapping("uni")
    @ResponseBody
	public Object uniList(@RequestBody SourceCriteria criteria) {
        Page<PictureInformation> sources = uniSourceRepository.fetchCustom(criteria);

        return responseOk()
            .results(sources)
            .put("op", "find")
            .put("criteria", criteria)
		    .build();
	}

	@PostMapping("findPath")
    @ResponseBody
	public Object listPath(@RequestBody SourceCriteria criteria) {
		Page<FolderStats> sourcePaths = uniSourceRepository.fetchPathCustom(criteria);

        return responseOk()
            .results(sourcePaths)
            .put("op", "findPath")
            .put("criteria", criteria)
		    .build();
	}

	@PostMapping("findDates")
    @ResponseBody
	public Object listDates(@RequestBody SourceCriteria criteria) {
		List<DateStats> dateStats = uniSourceRepository.fetchDatesCustom(criteria);

        return responseOk()
            .results(dateStats)
            .put("op", "findDates")
            .put("criteria", criteria)
		    .build();
	}

    @GetMapping(value = {"list/", "list/{parentId}"} )
    @ResponseBody
    public Object listByParent(@PathVariable("parentId") Optional<String> parentId) {
        Page<PictureInformation> page = uniSourceRepository.findByFilePath(
                parentId.isPresent()? parentId.get() : null,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));

        return responseOk().put("response", page).build();
    }

    @GetMapping(value = {"match/{grade}/{sourceId}"} )
    @ResponseBody
    public Object matchSource(@PathVariable("grade") PictureGrade grade, @PathVariable("sourceId") String id) {
        PictureInformation source = uniSourceRepository.fetchOne(id, grade.getEntityClass());

        SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(source);
        if (matchReport == null) {
            return responseError("Failed to match")
                    .put("id", id)
                    .put("grade", grade)
                    .result(source)
                    .build();
        }
        ResponseBuilder responseBuilder = responseOk()
                .put("id", id)
                .put("grade", grade)
                .result(source);
        if (matchReport.getPictures().size() == 1){
            responseBuilder.addMessage("Pictures found");
        }
        responseBuilder.put("report", matchReport);

        return responseBuilder.build();
    }

    @PostMapping(value = {"stats/"} )
    @ResponseBody
    public Object getFolderStats(@RequestBody String folderPath) {
        SourceFolderStats stats = uniSourceRepository.getFolderStats(appConfig.getSourcesRootFolder(), folderPath, PictureGrade.GALLERY.getEntityClass());

        if (stats == null) {
            return responseError("Failed to get folder stats")
                    .put("folderPath", folderPath)
                    .build();
        }
        ResponseBuilder responseBuilder = responseOk()
                .put("folderPath", folderPath)
                .result(stats);

        return responseBuilder.build();
    }

}

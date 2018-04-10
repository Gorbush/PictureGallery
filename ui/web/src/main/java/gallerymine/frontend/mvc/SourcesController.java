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
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.helpers.matchers.SourceFilesMatcher;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseError;
import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;

/**
 * @author Rob Winch
 * @author Doo-Hwan Kwak
 */
@Controller
@RequestMapping("/sources")
public class SourcesController {

	private static Logger log = LoggerFactory.getLogger(SourcesController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
	private SourceRepository sourceRepository;

	@Autowired
    private PictureRepository pictureRepository;

	@Autowired
    private SourceFilesMatcher sourceFilesMatcher;

	public SourcesController() {
	}

    @PostMapping("find")
    @ResponseBody
	public Object list(@RequestBody SourceCriteria criteria) {

		Page<Source> sources = sourceRepository.fetchCustom(criteria);

        return responseOk()
            .put("list", sources)
            .put("op", "find")
            .put("criteria", criteria)
		    .build();
	}

	@PostMapping("findPath")
    @ResponseBody
	public Object listPath(@RequestBody SourceCriteria criteria) {

		Page<FolderStats> sourcePaths = sourceRepository.fetchPathCustom(criteria);

        return responseOk()
            .put("list", sourcePaths)
            .put("op", "findPath")
            .put("criteria", criteria)
		    .build();
	}

	@PostMapping("findDates")
    @ResponseBody
	public Object listDates(@RequestBody SourceCriteria criteria) {

		List<DateStats> dateStats = sourceRepository.fetchDatesCustom(criteria);

        return responseOk()
            .put("list", dateStats)
            .put("op", "findDates")
            .put("criteria", criteria)
		    .build();
	}

    @GetMapping(value = {"list/", "list/{parentId}"} )
    @ResponseBody
    public Object listByParent(@PathVariable("parentId") Optional<String> parentId) {
        Page<Source> page = sourceRepository.findByFilePath(
                parentId.isPresent()? parentId.get() : null,
                new PageRequest(0, 500, new Sort(new Sort.Order(Sort.Direction.DESC, "id"))));

        return responseOk().put("response", page).build();
    }

    @GetMapping(value = {"match/{sourceId}"} )
    @ResponseBody
    public Object matchSource(@PathVariable("sourceId") String sourceId) {
        Source source = sourceRepository.findOne(sourceId);

        SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(source);
        if (matchReport == null) {
            return responseError("Failed to match")
                    .put("sourceId", sourceId)
                    .put("source", source).build();
        }
        ResponseBuilder responseBuilder = responseOk()
                .put("sourceId", sourceId)
                .put("source", source);
        if (matchReport.getPictures().size() == 1 && matchReport.getPictures().iterator().next().getId() == null){
            responseBuilder.addMessage("New picture created");
        } else {
            responseBuilder.addMessage("Pictures found");
        }
        responseBuilder.put("report", matchReport);

        return responseBuilder.build();
    }

    @PostMapping(value = {"stats/"} )
    @ResponseBody
    public Object getFolderStats(@RequestBody String folderPath) {

        SourceFolderStats stats = sourceRepository.getFolderStats(appConfig.getSourcesRootFolder(), folderPath);

        if (stats == null) {
            return responseError("Failed to get folder stats")
                    .put("folderPath", folderPath)
                    .build();
        }
        ResponseBuilder responseBuilder = responseOk()
                .put("folderPath", folderPath)
                .put("stats", stats);

        return responseBuilder.build();
    }

    @PostMapping(value = {"match/decisions"} )
    @ResponseBody
    public Object applyDecisions(@RequestBody List<ActionRequest> decisions) {
        ResponseBuilder responseBuilder = responseOk();
        int i = 0;
        for (ActionRequest action: decisions) {
            String id = action.getFirstOperand();
            if (id == null) {
                responseBuilder.addMessage("Decision failed for index=%d as id was null", i);
                continue;
            }
            Source source = sourceRepository.findOne(id);
            if (source == null) {
                responseBuilder.addMessage("Decision failed for index=%d id=%s as source not found", i, id);
                continue;
            }
            switch (action.getKind()) {
                case APPROVE: {
                    source.setKind(SourceKind.PRIMARY);
                    responseBuilder.addMessage("index=%d id=%s marked as PRIMARY", i, id);
                    break;
                }
                case DUPLICATE: {
                    source.setKind(SourceKind.DUPLICATE);
                    responseBuilder.addMessage("index=%d id=%s marked as DUPLICATE", i, id);
                    break;
                }
                case LATER: {
                    source.setKind(SourceKind.UNSET);
                    responseBuilder.addMessage("index=%d id=%s marked as UNSET", i, id);
                    break;
                }
                default: {
                    log.error("Unpredicted action KIND! {} for i={} and id={}", action.getKind(), i, id);
                }
            }
            sourceRepository.save(source);

            i++;
        }
        return responseBuilder.build();
    }

    @PostMapping(value = {"match/{rule}"} )
    @ResponseBody
    public Object matchSourcesFolder(@PathVariable("rule") FolderMatchRule rule, @RequestBody String folderPath) {
        Collection<Source> sources = sourceRepository.findByFilePath(folderPath);

        SourceFolderMatchReport folderMatchReport = new SourceFolderMatchReport();
        ResponseBuilder responseBuilder = responseOk();

        for (Source source : sources) {
            SourceMatchReport matchReport = sourceFilesMatcher.matchSourceTo(source);
            if (matchReport == null) {
                return responseError("Failed to match")
                        .put("rule", rule)
                        .put("folderPath", folderPath)
                        .build();
            }
            if (matchReport.getPictures().size() == 1 && matchReport.getPictures().iterator().next().getId() == null){
                responseBuilder.addMessage("New picture created");
            } else {
                responseBuilder.addMessage("Pictures found");
            }

        }
        responseBuilder
                .put("rule", rule)
                .put("report", folderMatchReport)
                .put("folderPath", folderPath);

        return responseBuilder.build();
    }

}

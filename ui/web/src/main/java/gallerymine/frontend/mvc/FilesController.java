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
import gallerymine.backend.beans.repository.FileRepository;
import gallerymine.backend.beans.repository.PictureRepository;
import gallerymine.backend.beans.repository.SourceRepository;
import gallerymine.backend.helpers.matchers.SourceFilesMatcher;
import gallerymine.frontend.mvc.support.ResponseBuilder;
import gallerymine.model.FileInformation;
import gallerymine.model.Source;
import gallerymine.model.importer.ActionRequest;
import gallerymine.model.mvc.FileCriteria;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.mvc.SourceCriteria;
import gallerymine.model.support.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static gallerymine.frontend.mvc.support.ResponseBuilder.responseOk;

/**
 */
@Controller
@RequestMapping("/files")
public class FilesController {

	private static Logger log = LoggerFactory.getLogger(FilesController.class);

	@Autowired
	public AppConfig appConfig;

	@Autowired
	private FileRepository fileRepository;

	public FilesController() {
	}

    @PostMapping("find")
    @ResponseBody
	public Object list(@RequestBody FileCriteria criteria) {
		Page<FileInformation> sources = fileRepository.fetchCustom(criteria);

        return responseOk()
            .put("list", sources)
            .put("op", "find")
            .put("criteria", criteria)
		    .build();
	}

    @GetMapping("{fileName}")
    @ResponseBody
	public String findFile(@PathVariable String fileName) {
		Collection<FileInformation> sources = fileRepository.findByFileName(fileName);

        return sources.stream().map(FileInformation::getLocation).collect( Collectors.joining( "\n" ) );
	}

    @GetMapping("{size}/{fileName}")
    @ResponseBody
	public String findFile(HttpServletResponse response, @PathVariable Long size,@PathVariable String fileName) {
		Collection<FileInformation> sources;

		if (size != null) {
			sources = fileRepository.findByFileNameAndSize(fileName, size);
		} else {
			sources = fileRepository.findByFileName(fileName);
		}
		if (sources.size() == 0) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return "";
		} else {
			return sources.stream().map(FileInformation::getLocation).collect(Collectors.joining("\n"));
		}
	}

    @GetMapping("{storage}/{size}/{fileName}")
    @ResponseBody
	public String findFile(@PathVariable("storage") String storage,
								   @PathVariable Long size,
								   @PathVariable String fileName) {
		Collection<FileInformation> sources;

		sources = fileRepository.findByStorageAndFileNameAndSize(storage, fileName, size);

        return sources.stream().map(FileInformation::getLocation).collect( Collectors.joining( "\n" ) );
	}

	@PutMapping(value = {"/{storage}/{size}/{stamp}/**"} )
	@ResponseBody
	public String putFile(HttpServletRequest request,
						  HttpServletResponse response,
						  @PathVariable("storage") String storage,
						  @PathVariable("size") long size,
						  @PathVariable("stamp") String stampTicks) {
		String pattern = (String)request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		String fullFileName = new AntPathMatcher().extractPathWithinPattern(pattern,request.getServletPath());

		Path file = Paths.get(fullFileName);

		String fileName = file.toFile().getName();
		String filePath = file.toFile().getParent();

		Collection<FileInformation> files = fileRepository.findByFilePathAndFileName(filePath, fileName);

		if (files.size() > 0) {
			// should be return code 303
			response.setStatus(303);
			return files.stream().map(FileInformation::getLocation).collect( Collectors.joining( "\n" ) );
		}

		FileInformation newInfo = new FileInformation();
		newInfo.setStorage(storage);
		newInfo.setFileName(fileName);
		newInfo.setFilePath(filePath);
		newInfo.setSize(size);
		newInfo.setExists(true);
		DateTime stamp = null;
		if (StringUtils.isNotBlank(stampTicks)) {
			try {
				stamp = new DateTime(stampTicks);
			} catch (Exception e) {
				try {
					Date date = DateUtils.parseDate(stampTicks,
							"yyyy-MM-dd' 'HH:mm:ss",
							"yyyy-MM-dd'T'HH:mm:ss");
					stamp = new DateTime(date);
				} catch (Exception e2) {
					log.warn("Failed to parse time stamp %s for file %s", stampTicks, fileName, e);
				}
			}
			if (stamp != null) {
				newInfo.setTimestamp(stamp);
				newInfo.addStamp(TimestampKind.TS_UNKNOWN.create(stamp));
			}
		}
		FileInformation info = fileRepository.save(newInfo);

		return info.getId();
	}

	@PostMapping(value = {"/put"} )
	@ResponseBody
	public String putFileBody(HttpServletRequest request,
						  HttpServletResponse response,
						  @RequestBody FileInformation newInfo) {
		Collection<FileInformation> files = fileRepository.findByFilePathAndFileName(newInfo.getFilePath(), newInfo.getFileName());

		if (files.size() > 0) {
			// should be return code 303
			response.setStatus(303);
			return files.stream().map(FileInformation::getLocation).collect( Collectors.joining( "\n" ) );
		}

		newInfo.setExists(true);
		if (newInfo.getTimestamp() != null) {
			newInfo.addStamp(TimestampKind.TS_UNKNOWN.create(newInfo.getTimestamp()));
		}

		FileInformation info = fileRepository.save(newInfo);

		return info.getId();
	}

}

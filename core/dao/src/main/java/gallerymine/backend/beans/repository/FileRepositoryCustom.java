/*
 * Copyright 2012-2013 the original author or authors.
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

package gallerymine.backend.beans.repository;

import gallerymine.model.FileInformation;
import gallerymine.model.PictureInformation;
import gallerymine.model.mvc.FileCriteria;
import gallerymine.model.mvc.FolderStats;
import gallerymine.model.support.DateStats;
import gallerymine.model.support.SourceFolderStats;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@Repository()
public interface FileRepositoryCustom<Information extends FileInformation, RequestCriteria extends FileCriteria> {

	Page<Information> fetchCustom(RequestCriteria criteria);
	<T extends Information> Page<T> fetchCustom(RequestCriteria searchCriteria, Class<T> clazz);
	<T extends Information> Iterator<T> fetchCustomStream(RequestCriteria sourceCriteria, Class<T> clazz);

	<T extends Information> Page<FolderStats> fetchPathCustom(RequestCriteria sourceCriteria, Class<T> clazz);
	<T extends Information> List<DateStats> fetchDatesCustom(RequestCriteria sourceCriteria, Class<T> clazz);
	<T extends Information> SourceFolderStats getFolderStats(String rootFolder, String folderPath, Class<T> clazz);

	<T extends PictureInformation> T fetchOne(String id, Class<T> clazz);
	<T extends PictureInformation> boolean deleteByGrade(String id, Class<T> clazz);

}

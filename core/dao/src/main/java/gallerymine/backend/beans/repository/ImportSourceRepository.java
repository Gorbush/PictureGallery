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

import com.mongodb.client.model.geojson.Point;
import gallerymine.model.ImportSource;
import gallerymine.model.Source;
import org.joda.time.DateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

//@RepositoryRestResource(collectionResourceRel = "source", path = "source")
@Repository
public interface ImportSourceRepository extends MongoRepository<ImportSource, String>, ImportSourceRepositoryCustom {

    Collection<ImportSource> findByFilePath(@Param("filePath") String filePath);

    Page<ImportSource> findByFilePath(@Param("filePath") String filePath, Pageable pageable);

    ImportSource findByFilePathAndFileName(@Param("filePath") String filePath, @Param("fileName") String fileName);

    Collection<ImportSource> findByFileName(String fileName);
    Collection<ImportSource> findByFileNameAndSize(String fileName, long size);

    Collection<ImportSource> findBySize(long size);

    Collection<ImportSource> findByTimestamp(DateTime timestamp);

    Page<ImportSource> findByGeoLocationNear(Point point, Pageable pageable);
}

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
public interface SourceRepository extends MongoRepository<Source, String>, SourceRepositoryCustom {
//    @Query(value="{path : ?0}", fields="{path : 0}")
    Collection<Source> findByFilePath(@Param("filePath") String filePath);

    Page<Source> findByFilePath(@Param("filePath") String filePath, Pageable pageable);

    Source findByFilePathAndFileName(@Param("filePath") String filePath, @Param("fileName") String fileName);

    Collection<Source> findByFileName(String fileName);

    Collection<Source> findBySize(long size);

    Collection<Source> findByTimestamp(DateTime timestamp);
}

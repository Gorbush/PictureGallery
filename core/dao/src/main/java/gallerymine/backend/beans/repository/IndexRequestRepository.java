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

import gallerymine.model.importer.IndexRequest;
import gallerymine.model.importer.ThumbRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

//@RepositoryRestResource(collectionResourceRel = "indexRequests", path = "indexRequests")
@Repository
public interface IndexRequestRepository extends MongoRepository<IndexRequest, String> {

    IndexRequest findByPath(@Param("path") String path);

    Page<IndexRequest> findByStatus(@Param("status") IndexRequest.IndexStatus status, Pageable pageable);

    Page<IndexRequest> findByParent(String parent, Pageable pageable);

    Page<IndexRequest> findByParentNull(Pageable pageable);
}

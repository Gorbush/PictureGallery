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

import gallerymine.model.importer.ImportRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;

@Repository
public interface ImportRequestRepository extends MongoRepository<ImportRequest, String> {

    ImportRequest findByPath(@Param("path") String path);

    ImportRequest findByOriginalPath(@Param("originalPath") String path);

    Page<ImportRequest> findByStatus(@Param("status") ImportRequest.ImportStatus status, Pageable pageable);

    Page<ImportRequest> findByStatusIn(@Param("status") Collection<ImportRequest.ImportStatus> statuses, Pageable pageable);

    Page<ImportRequest> findByParent(String parent, Pageable pageable);

    Page<ImportRequest> findByParentAndIndexProcessIds(String parent, String processId, Pageable pageable);

    @Query(value="{indexProcessIds: ?0, parent: { $exists: false } }")
    Page<ImportRequest> findRootIndexes(String processId, Pageable pageable);

    @Query(value="{indexProcessIds: ?0, $expr:{$eq:[\"$parent\", \"$rootId\"]} }")
    Page<ImportRequest> findSubRootIndexes(String processId, Pageable pageable);

    Page<ImportRequest> findByParentNull(Pageable pageable);

    ImportRequest findByIndexProcessIdsContains(@Param("processId") String processId);

    Collection<ImportRequest> findAllByIndexProcessIdsIsContainingAndParentIsNullOrderByCreatedAsc(@Param("indexProcessId") String processId);

    Collection<ImportRequest> findByStatus(Collection<ImportRequest.ImportStatus> statuses);

    @Query(value="{indexProcessIds: ?0 }", fields="{updated : 0}")
    ImportRequest findLastUpdated(String requestId, Sort sort);

    @Query(value="{parent: ?0, status: 'APPROVING' }")
    Stream<ImportRequest> findByParentForApprove(String parent);
}

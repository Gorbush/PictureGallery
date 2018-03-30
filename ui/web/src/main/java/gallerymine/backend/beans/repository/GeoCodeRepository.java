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

import gallerymine.model.GeoCodePlace;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

//@RepositoryRestResource(collectionResourceRel = "geoCodes", path = "geoCodes")
@Repository()
public interface GeoCodeRepository extends MongoRepository<GeoCodePlace, String> {

//	@Query("select g from GeoCodePlace where g.point")
//	GeoCodePlace findByCoords(double latitude, double longitude);

//    List<GeoCodePlace> byParent(String parentId);

//    List<GeoCodePlace> findByPointWithin(Polygon polygon);

//    List<GeoCodePlace> findAllByPlace(String placePath, boolean oneLevel);

//    GeoCodePlace findByPlace(String placePath);
}

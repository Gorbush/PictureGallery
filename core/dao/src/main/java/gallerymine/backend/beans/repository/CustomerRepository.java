package gallerymine.backend.beans.repository;

import java.util.List;

import gallerymine.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

//@RepositoryRestResource(collectionResourceRel = "customers", path = "customers")
@Repository()
public interface CustomerRepository extends MongoRepository<Customer, String> {

	Customer findByFirstName(@Param("firstName") String firstName);

	List<Customer> findByLastName(@Param("lastName") String lastName);

	Page<Customer> findByFirstNameContainingAndLastNameContainingAllIgnoringCase(
			@Param("firstName") String firstName, @Param("lastName") String lastName,
			Pageable pageable);

}

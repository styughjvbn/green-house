package com.greenhouse.backend.sales.repository;

import com.greenhouse.backend.sales.domain.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

	List<Customer> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

	Optional<Customer> findByNameIgnoreCase(String name);
}

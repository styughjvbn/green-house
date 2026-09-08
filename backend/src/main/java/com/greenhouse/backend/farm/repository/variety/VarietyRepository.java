package com.greenhouse.backend.farm.repository.variety;

import com.greenhouse.backend.farm.domain.variety.Variety;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VarietyRepository extends JpaRepository<Variety, Long>, VarietyRepositoryCustom {

	// PostgreSQL sequence allocation is atomic and is never inferred from a previously
	// read row.
	@Query(value = "select nextval('variety_codes_seq')", nativeQuery = true)
	long nextCodeValue();

	Optional<Variety> findByGenusAndName(String genus, String name);

	boolean existsByGenusAndName(String genus, String name);

	boolean existsByGenusAndNameAndIdNot(String genus, String name, Long id);

}

package com.greenhouse.backend.farm.repository.material;

import com.greenhouse.backend.farm.domain.material.Material;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MaterialRepository extends JpaRepository<Material, Long>, MaterialRepositoryCustom {

	// PostgreSQL sequence allocation is atomic and is never inferred from a previously
	// read row.
	@Query(value = "select nextval('material_codes_seq')", nativeQuery = true)
	long nextCodeValue();

}

package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.Material;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long>, MaterialRepositoryCustom {

	Optional<Material> findTopByOrderByIdDesc();
}

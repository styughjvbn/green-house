package com.greenhouse.backend.farm.repository.material;

import com.greenhouse.backend.farm.domain.material.Material;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, Long>, MaterialRepositoryCustom {

	Optional<Material> findTopByOrderByIdDesc();
}

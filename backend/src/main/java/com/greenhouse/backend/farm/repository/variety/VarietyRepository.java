package com.greenhouse.backend.farm.repository.variety;

import com.greenhouse.backend.farm.domain.variety.Variety;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VarietyRepository extends JpaRepository<Variety, Long>, VarietyRepositoryCustom {

	Optional<Variety> findTopByOrderByIdDesc();

	Optional<Variety> findByGenusAndName(String genus, String name);

	boolean existsByGenusAndName(String genus, String name);

	boolean existsByGenusAndNameAndIdNot(String genus, String name, Long id);

}

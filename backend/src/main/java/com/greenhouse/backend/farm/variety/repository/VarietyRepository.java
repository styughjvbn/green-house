package com.greenhouse.backend.farm.variety.repository;

import com.greenhouse.backend.farm.variety.domain.Variety;
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

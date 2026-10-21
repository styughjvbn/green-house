package com.greenhouse.backend.farm.variety.repository;

import com.greenhouse.backend.farm.variety.domain.Variety;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VarietyRepository extends JpaRepository<Variety, Long>, VarietyRepositoryCustom {

  @Query(
      """
      select distinct v.name from Variety v
      where v.active = true
        and exists (select g.id from OrchidGroup g where g.variety = v)
      order by v.name
      """)
  List<String> findActiveNamesWithLinkedGroups();

  // PostgreSQL sequence allocation is atomic and is never inferred from a previously
  // read row.
  @Query(value = "select nextval('variety_codes_seq')", nativeQuery = true)
  long nextCodeValue();

  Optional<Variety> findByGenusAndName(String genus, String name);

  boolean existsByGenusAndName(String genus, String name);

  boolean existsByGenusAndNameAndIdNot(String genus, String name, Long id);
}

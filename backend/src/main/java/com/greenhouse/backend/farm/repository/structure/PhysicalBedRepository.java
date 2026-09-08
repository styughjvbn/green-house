package com.greenhouse.backend.farm.repository.structure;

import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalBedRepository extends JpaRepository<PhysicalBed, Long> {

	@EntityGraph(attributePaths = { "house", "bedZones" })
	List<PhysicalBed> findByHouseIdOrderByDisplayOrderAsc(Long houseId);

	@Query("""
			select distinct b from PhysicalBed b
			join fetch b.house h
			left join fetch b.bedZones z
			where b.id in :bedIds
			order by h.number asc, b.displayOrder asc, z.sortOrder asc
			""")
	List<PhysicalBed> findAllWithZonesByIdIn(@Param("bedIds") java.util.Collection<Long> bedIds);

	@Query("""
			select distinct b from PhysicalBed b
			join fetch b.house h
			order by h.number asc, b.displayOrder asc
			""")
	@EntityGraph(attributePaths = { "house", "bedZones" })
	List<PhysicalBed> findAllInFarmOrder();

	@Query("""
			select new com.greenhouse.backend.farm.repository.structure.PhysicalBedOrderRow(
				b.id, h.id, h.number, b.number)
			from PhysicalBed b
			join b.house h
			order by h.number asc, b.displayOrder asc
			""")
	List<PhysicalBedOrderRow> findAllOrderRows();

	@EntityGraph(attributePaths = { "house", "bedZones" })
	Optional<PhysicalBed> findWithHouseAndBedZonesById(Long id);

}

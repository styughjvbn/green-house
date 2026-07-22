package com.greenhouse.backend.farm.repository.structure;

import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedZoneRepository extends JpaRepository<BedZone, Long> {

	List<BedZone> findByPhysicalBedIdOrderBySortOrderAsc(Long physicalBedId);

	@Query("""
			select z from BedZone z
			join z.physicalBed b
			where b.house.id = :houseId
			order by b.displayOrder asc, z.sortOrder asc
			""")
	List<BedZone> findByHouseId(@Param("houseId") Long houseId);

	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house", "orchidGroups", "capacities" })
	Optional<BedZone> findWithDetailsById(Long id);

	@Query("""
			select z from BedZone z
			join z.physicalBed b
			join b.house h
			where h.number = :houseNumber and b.number = :physicalBedNumber and z.side = :side
			""")
	Optional<BedZone> findSeedZone(
			@Param("houseNumber") Integer houseNumber,
			@Param("physicalBedNumber") Integer physicalBedNumber,
			@Param("side") BedZoneSide side);
}

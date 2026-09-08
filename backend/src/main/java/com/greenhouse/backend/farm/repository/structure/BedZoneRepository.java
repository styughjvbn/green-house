package com.greenhouse.backend.farm.repository.structure;

import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BedZoneRepository extends JpaRepository<BedZone, Long> {

	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house" })
	List<BedZone> findByPhysicalBedIdOrderBySortOrderAsc(Long physicalBedId);

	@Query("""
			select z from BedZone z
			join z.physicalBed b
			where b.house.id = :houseId
			order by b.displayOrder asc, z.sortOrder asc
			""")
	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house" })
	List<BedZone> findByHouseId(@Param("houseId") Long houseId);

	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house" })
	@Query("select z from BedZone z order by z.id")
	List<BedZone> findAllWithLocation();

	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house" })
	Optional<BedZone> findWithLocationById(Long id);

	@EntityGraph(attributePaths = { "physicalBed", "physicalBed.house", "orchidGroups", "capacities" })
	Optional<BedZone> findWithDetailsById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select z from BedZone z join fetch z.physicalBed b join fetch b.house where z.id = :id")
	Optional<BedZone> findForUpdateById(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select z from BedZone z
			join fetch z.physicalBed b
			join fetch b.house
			where z.id in :ids
			order by z.id
			""")
	List<BedZone> findAllForUpdateByIdIn(@Param("ids") java.util.Collection<Long> ids);

	@Query("""
			select z from BedZone z
			join z.physicalBed b
			join b.house h
			where h.number = :houseNumber and b.number = :physicalBedNumber and z.side = :side
			""")
	Optional<BedZone> findSeedZone(@Param("houseNumber") Integer houseNumber,
			@Param("physicalBedNumber") Integer physicalBedNumber, @Param("side") BedZoneSide side);

	@Query("""
			select new com.greenhouse.backend.farm.repository.structure.BedZoneLocationRow(
				z.id, h.number, b.number, z.name)
			from BedZone z
			join z.physicalBed b
			join b.house h
			where z.id in :bedZoneIds
			order by z.id asc
			""")
	List<BedZoneLocationRow> findLocationRowsByIdIn(@Param("bedZoneIds") java.util.Collection<Long> bedZoneIds);

}

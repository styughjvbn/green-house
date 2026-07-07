package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrchidGroupRepository extends JpaRepository<OrchidGroup, Long> {

	boolean existsByVarietyName(String varietyName);

	@Query("select coalesce(max(g.sortOrder), 0) from OrchidGroup g where g.bedZone.id = :bedZoneId")
	int findMaxSortOrderByBedZoneId(@Param("bedZoneId") Long bedZoneId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			where (:houseId is null or h.id = :houseId)
				and (:physicalBedId is null or b.id = :physicalBedId)
				and (:bedZoneId is null or z.id = :bedZoneId)
				and (:status is null or g.status = :status)
			order by h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> search(
			@Param("houseId") Long houseId,
			@Param("physicalBedId") Long physicalBedId,
			@Param("bedZoneId") Long bedZoneId,
			@Param("status") String status);

	@Query("""
			select count(g) from OrchidGroup g
			join g.bedZone z
			join z.physicalBed b
			where b.house.id = :houseId
			""")
	long countByHouseId(@Param("houseId") Long houseId);

	@Query("""
			select count(g) from OrchidGroup g
			where g.status in ('주의', '이상', '병해충')
			""")
	long countWarningStatus();

	@Query("""
			select count(g) from OrchidGroup g
			join g.bedZone z
			join z.physicalBed b
			where b.house.id = :houseId and g.status in ('주의', '이상', '병해충')
			""")
	long countWarningStatusByHouseId(@Param("houseId") Long houseId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			where g.variety.id = :varietyId
			order by h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> findByVarietyIdOrderByLocation(@Param("varietyId") Long varietyId);

	boolean existsByVarietyId(Long varietyId);

}

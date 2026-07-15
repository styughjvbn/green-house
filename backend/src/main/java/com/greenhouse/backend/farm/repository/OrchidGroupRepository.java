package com.greenhouse.backend.farm.repository;

import com.greenhouse.backend.farm.domain.OrchidGroup;
import com.greenhouse.backend.farm.domain.PotSizeCode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface OrchidGroupRepository extends JpaRepository<OrchidGroup, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select g from OrchidGroup g where g.id in :orchidGroupIds")
	List<OrchidGroup> findAllForUpdateByIdIn(@Param("orchidGroupIds") java.util.Collection<Long> orchidGroupIds);

	boolean existsByVarietyName(String varietyName);

	@Query("select coalesce(max(g.sortOrder), 0) from OrchidGroup g where g.bedZone.id = :bedZoneId")
	int findMaxSortOrderByBedZoneId(@Param("bedZoneId") Long bedZoneId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			where (:houseId is null or h.id = :houseId)
			    and (:keyword = ''
			        or lower(g.varietyName) like concat('%', lower(:keyword), '%')
			        or lower(coalesce(g.genus, '')) like concat('%', lower(:keyword), '%')
			        or lower(coalesce(g.memo, '')) like concat('%', lower(:keyword), '%'))
			    and (:physicalBedId is null or b.id = :physicalBedId)
			    and (:bedZoneId is null or z.id = :bedZoneId)
			    and (:status is null or g.status = :status)
			    and g.quantity > 0
			order by h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> search(
			@Param("houseId") Long houseId,
			@Param("keyword") String keyword,
			@Param("physicalBedId") Long physicalBedId,
			@Param("bedZoneId") Long bedZoneId,
			@Param("status") String status);

	@Query("""
			select count(g) from OrchidGroup g
			join g.bedZone z
			join z.physicalBed b
			where b.house.id = :houseId
			  and g.quantity > 0
			""")
	long countByHouseId(@Param("houseId") Long houseId);

	@Query("""
			select count(g) from OrchidGroup g
			where g.status in ('주의', '이상', '병해충')
			  and g.quantity > 0
			""")
	long countWarningStatus();

	@Query("""
			select count(g) from OrchidGroup g
			join g.bedZone z
			join z.physicalBed b
			where b.house.id = :houseId and g.status in ('주의', '이상', '병해충') and g.quantity > 0
			""")
	long countWarningStatusByHouseId(@Param("houseId") Long houseId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			where g.variety.id = :varietyId
			  and g.quantity > 0
			order by h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> findByVarietyIdOrderByLocation(@Param("varietyId") Long varietyId);

	boolean existsByVarietyId(Long varietyId);

	List<OrchidGroup> findByVarietyIsNull();

	List<OrchidGroup> findByBedZoneIdAndQuantityGreaterThanOrderBySortOrderAsc(Long bedZoneId, Integer quantity);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			left join fetch g.variety v
			where (:keyword = ''
				or lower(g.varietyName) like lower(concat('%', :keyword, '%'))
				or lower(coalesce(g.genus, '')) like lower(concat('%', :keyword, '%'))
				or lower(concat(cast(h.number as string), '동 ', cast(b.number as string), '배드 ', z.name)) like lower(concat('%', :keyword, '%')))
			  and (:varietyId is null or v.id = :varietyId)
			  and (:status = '' or g.status = :status)
			  and (g.quantity - g.reservedQuantity) > 0
			order by h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> searchSellable(
			@Param("keyword") String keyword,
			@Param("varietyId") Long varietyId,
			@Param("status") String status);

	@Query("""
			select g from OrchidGroup g
			left join fetch g.variety
			left join fetch g.bedZone z
			left join fetch z.physicalBed b
			left join fetch b.house
			where g.id = :orchidGroupId
			""")
	Optional<OrchidGroup> findDetailById(@Param("orchidGroupId") Long orchidGroupId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			left join fetch g.variety
			left join fetch g.inboundRecord
			where h.id = :houseId
			  and g.quantity > 0
			  and g.status not in ('종료', '폐기', '판매 완료')
			order by b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> findActiveWorkTargetsByHouseId(@Param("houseId") Long houseId);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house
			left join fetch g.variety
			left join fetch g.inboundRecord
			where g.id in :orchidGroupIds
			  and g.quantity > 0
			  and g.status not in ('종료', '폐기', '판매 완료')
			""")
	List<OrchidGroup> findActiveWorkTargetsByIds(@Param("orchidGroupIds") java.util.Collection<Long> orchidGroupIds);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house
			left join fetch g.variety
			where g.id in :orchidGroupIds
			""")
	List<OrchidGroup> findDetailsByIds(@Param("orchidGroupIds") java.util.Collection<Long> orchidGroupIds);

	@Query("""
			select g from OrchidGroup g
			join fetch g.bedZone z
			join fetch z.physicalBed b
			join fetch b.house h
			join fetch g.variety v
			left join fetch g.inboundRecord
			where g.quantity > 0
			  and g.status not in ('종료', '폐기', '판매 완료')
			  and g.potSizeCode <> com.greenhouse.backend.farm.domain.PotSizeCode.UNMAPPED
			  and (:varietyId is null or v.id = :varietyId)
			  and (:potSizeCode is null or g.potSizeCode = :potSizeCode)
			  and (:houseId is null or h.id = :houseId)
			  and (:status = '' or g.status = :status)
			  and (:keyword = ''
			      or lower(v.name) like lower(concat('%', :keyword, '%'))
			      or lower(v.genus) like lower(concat('%', :keyword, '%')))
			order by v.name asc, g.ageYear asc, g.potSizeCode asc,
			         h.number asc, b.displayOrder asc, z.sortOrder asc, g.sortOrder asc
			""")
	List<OrchidGroup> findDerivedGroupCandidates(
			@Param("varietyId") Long varietyId,
			@Param("potSizeCode") PotSizeCode potSizeCode,
			@Param("houseId") Long houseId,
			@Param("status") String status,
			@Param("keyword") String keyword);
}

package com.greenhouse.backend.farm.repository.orchid;

import static com.greenhouse.backend.farm.domain.inbound.QInboundRecord.inboundRecord;
import static com.greenhouse.backend.farm.domain.orchid.QOrchidGroup.orchidGroup;
import static com.greenhouse.backend.farm.domain.structure.QBedZone.bedZone;
import static com.greenhouse.backend.farm.domain.structure.QHouse.house;
import static com.greenhouse.backend.farm.domain.structure.QPhysicalBed.physicalBed;
import static com.greenhouse.backend.farm.domain.variety.QVariety.variety;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroupStatusPolicy;
import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.jpa.HibernateHints;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrchidGroupSummaryRepository {
  private static final int FETCH_SIZE = 500;
  private final JPAQueryFactory queries;

  public List<VarietyInventorySummary> summarizeVarieties(Collection<Long> varietyIds) {
    var available =
        new CaseBuilder()
            .when(orchidGroup.quantity.gt(orchidGroup.reservedQuantity))
            .then(orchidGroup.quantity.subtract(orchidGroup.reservedQuantity))
            .otherwise(0);
    var saleable =
        new CaseBuilder()
            .when(orchidGroup.status.notIn(OrchidGroupStatusPolicy.unavailableForSaleStatuses()))
            .then(available)
            .otherwise(0);
    return queries
        .select(
            Projections.constructor(
                VarietyInventorySummary.class,
                orchidGroup.variety.id,
                orchidGroup.id.count(),
                orchidGroup.quantity.sum().longValue(),
                saleable.sum().longValue()))
        .from(orchidGroup)
        .where(orchidGroup.variety.id.in(varietyIds), orchidGroup.quantity.gt(0))
        .groupBy(orchidGroup.variety.id)
        .fetch();
  }

  public Stream<OrchidGroupVarietyReference> streamVarietyReferences(Collection<Long> varietyIds) {
    return queries
        .select(
            Projections.constructor(
                OrchidGroupVarietyReference.class, orchidGroup.id, orchidGroup.variety.id))
        .from(orchidGroup)
        .where(orchidGroup.variety.id.in(varietyIds), orchidGroup.quantity.gt(0))
        .orderBy(orchidGroup.id.asc())
        .setHint(HibernateHints.HINT_FETCH_SIZE, FETCH_SIZE)
        .stream();
  }

  public Stream<DerivedOrchidGroupSummaryRow> streamDerivedSummaries(
      Long varietyId, PotSizeCode potSizeCode, Long houseId, String status, String keyword) {
    return candidates(varietyId, potSizeCode, houseId, status, keyword)
        .select(
            Projections.constructor(
                DerivedOrchidGroupSummaryRow.class,
                variety.id,
                variety.name,
                variety.genus,
                orchidGroup.ageYear,
                inboundRecord.inboundDate,
                orchidGroup.createdAt,
                orchidGroup.potSizeCode,
                orchidGroup.potSize,
                orchidGroup.quantity,
                bedZone.id))
        .setHint(HibernateHints.HINT_FETCH_SIZE, FETCH_SIZE)
        .stream();
  }

  public Stream<DerivedOrchidGroupMemberRow> streamDerivedMembers(
      Long varietyId, PotSizeCode potSizeCode, Long houseId, String status, String keyword) {
    return candidates(varietyId, potSizeCode, houseId, status, keyword)
        .select(
            Projections.constructor(
                DerivedOrchidGroupMemberRow.class,
                orchidGroup.id,
                bedZone.id,
                variety.id,
                variety.color,
                variety.genus,
                variety.name,
                orchidGroup.quantity,
                orchidGroup.potSize,
                orchidGroup.potSizeCode,
                orchidGroup.ageYear,
                orchidGroup.status,
                orchidGroup.placementType,
                orchidGroup.trayCount,
                orchidGroup.splitPlacementAllowed,
                orchidGroup.startPosition,
                orchidGroup.endPosition,
                orchidGroup.sortOrder,
                orchidGroup.memo,
                house.id,
                house.number,
                physicalBed.number,
                bedZone.name,
                inboundRecord.inboundDate,
                orchidGroup.createdAt))
        .setHint(HibernateHints.HINT_FETCH_SIZE, FETCH_SIZE)
        .stream();
  }

  private JPAQuery<?> candidates(
      Long varietyId, PotSizeCode potSizeCode, Long houseId, String status, String keyword) {
    return queries
        .from(orchidGroup)
        .join(orchidGroup.bedZone, bedZone)
        .join(bedZone.physicalBed, physicalBed)
        .join(physicalBed.house, house)
        .join(orchidGroup.variety, variety)
        .leftJoin(orchidGroup.inboundRecord, inboundRecord)
        .where(
            orchidGroup.quantity.gt(0),
            orchidGroup.status.notIn(OrchidGroupStatusPolicy.inactiveStatuses()),
            orchidGroup.potSizeCode.ne(PotSizeCode.UNMAPPED),
            varietyId == null ? null : variety.id.eq(varietyId),
            potSizeCode == null ? null : orchidGroup.potSizeCode.eq(potSizeCode),
            houseId == null ? null : house.id.eq(houseId),
            status.isEmpty() ? null : orchidGroup.status.eq(status),
            keyword.isEmpty()
                ? null
                : variety
                    .name
                    .lower()
                    .like(Expressions.asString("%" + keyword + "%").lower())
                    .or(
                        variety
                            .genus
                            .lower()
                            .like(Expressions.asString("%" + keyword + "%").lower())))
        .orderBy(
            variety.name.asc(),
            orchidGroup.ageYear.asc(),
            orchidGroup.potSizeCode.asc(),
            house.number.asc(),
            physicalBed.displayOrder.asc(),
            bedZone.sortOrder.asc(),
            orchidGroup.sortOrder.asc());
  }
}

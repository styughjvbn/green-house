package com.greenhouse.backend.settlement.repository;

import com.greenhouse.backend.settlement.domain.AuctionSettlement;
import com.greenhouse.backend.settlement.domain.AuctionSettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuctionSettlementRepository extends JpaRepository<AuctionSettlement, Long> {

  String FILTERS =
      """
			from AuctionSettlement settlement
			where (:auctionHouseId is null or settlement.auctionHouseId = :auctionHouseId)
			  and (cast(:fromDate as LocalDate) is null or settlement.auctionDate >= :fromDate)
			  and (cast(:toDate as LocalDate) is null or settlement.auctionDate <= :toDate)
			  and (:status is null or settlement.status = :status)
			""";

  @Query(
      "select distinct line.auctionShipmentLotId from AuctionSettlementLine line where line.auctionShipmentLotId in :lotIds")
  List<Long> findSettledLotIds(@Param("lotIds") Collection<Long> lotIds);

  // TODO: 별도 일괄 정산에서 재사용 시 빈 테이블에서 증가한 뒤의 cached generic 계획을 검증한다.
  // 20/500 ID 입력의 전체 스캔 재현 근거: backend-audit/archive/14-performance-diagnosis.md
  @Query(
      "select line.auctionResultLineId from AuctionSettlementLine line where line.auctionResultLineId in :resultIds")
  List<Long> findLinkedResultIds(@Param("resultIds") Collection<Long> resultIds);

  Optional<AuctionSettlement> findByAuctionHouseIdAndAuctionDate(
      Long auctionHouseId, LocalDate auctionDate);

  @EntityGraph(attributePaths = {"lines"})
  @Query(
      "select settlement from AuctionSettlement settlement "
          + "where settlement.auctionHouseId = :auctionHouseId and settlement.auctionDate = :auctionDate")
  Optional<AuctionSettlement> findWithLinesByHouseAndDate(
      Long auctionHouseId, LocalDate auctionDate);

  @Query(
      value =
          "select settlement "
              + FILTERS
              + "order by settlement.auctionDate desc, settlement.id desc",
      countQuery = "select count(settlement) " + FILTERS)
  Page<AuctionSettlement> search(
      @Param("auctionHouseId") Long auctionHouseId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("status") AuctionSettlementStatus status,
      Pageable pageable);

  @Query(
      "select coalesce(sum(settlement.expectedDepositAmount), 0) as expectedDepositAmount, "
          + "coalesce(sum(settlement.remainingAmount), 0) as remainingAmount "
          + FILTERS)
  Totals summarize(
      @Param("auctionHouseId") Long auctionHouseId,
      @Param("fromDate") LocalDate from,
      @Param("toDate") LocalDate to,
      @Param("status") AuctionSettlementStatus status);

  @EntityGraph(attributePaths = "lines")
  List<AuctionSettlement> findAllByIdInOrderByAuctionDateDescIdDesc(Collection<Long> ids);

  interface Totals {

    Long getExpectedDepositAmount();

    Long getRemainingAmount();
  }

  @EntityGraph(attributePaths = {"lines"})
  Optional<AuctionSettlement> findWithDetailsById(Long id);

  @Query(
      "select settlement.auctionHouseId from AuctionSettlement settlement where settlement.id = :id")
  Optional<Long> findAuctionHouseId(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select settlement from AuctionSettlement settlement where settlement.id = :id")
  Optional<AuctionSettlement> findForUpdateById(@Param("id") Long id);
}

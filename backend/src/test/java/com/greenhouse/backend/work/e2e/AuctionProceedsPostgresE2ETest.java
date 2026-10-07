package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.sales.application.auction.AuctionProceedsService;
import com.greenhouse.backend.sales.domain.auction.*;
import com.greenhouse.backend.sales.domain.partner.BusinessPartner;
import com.greenhouse.backend.sales.domain.partner.PartnerType;
import com.greenhouse.backend.sales.repository.auction.AuctionProceedsRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import com.greenhouse.backend.sales.repository.partner.BusinessPartnerRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@Tag("work-e2e")
class AuctionProceedsPostgresE2ETest extends WorkE2ETestBase {
  @Autowired AuctionProceedsService service;
  @Autowired AuctionProceedsRepository proceeds;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired BusinessPartnerRepository partners;

  @Test
  void persistsSuppliedAmountsAndBlocksDuplicateResultOwnership() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    Long id =
        service.record(fixture.houseId(), "경매장 결과 자료", 1000L, 930L, List.of(fixture.resultId()));
    assertThat(proceeds.findById(id).orElseThrow().isPaymentTargetReady()).isFalse();
    service.confirm(id, "확인자");
    var confirmed = proceeds.findById(id).orElseThrow();
    assertThat(confirmed.isPaymentTargetReady()).isTrue();
    assertThat(confirmed.getReceivableAmount()).isEqualTo(930);
    assertThatThrownBy(
            () ->
                service.record(
                    fixture.houseId(), "중복 자료", 1000L, 930L, List.of(fixture.resultId())))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(proceeds.findById(id).orElseThrow().isPaymentTargetReady()).isTrue();
  }

  @Test
  void preservesUnconfirmedEvidenceAndRollsBackFailedConfirmation() {
    var fixture = seed(AuctionInspectionStatus.MANUAL_REVIEW);
    Long id = service.record(fixture.houseId(), "검토할 자료", 1000L, null, List.of(fixture.resultId()));
    assertThatThrownBy(() -> service.confirm(id, "확인자")).isInstanceOf(ConflictException.class);
    var stored = proceeds.findById(id).orElseThrow();
    assertThat(stored.isMatchingConfirmed()).isFalse();
    assertThat(stored.getReceivableAmount()).isNull();
    assertThat(stored.getReportedGrossAmount()).isEqualTo(1000);
  }

  @Test
  void rejectsAnotherMarketsResultsWithoutSavingATarget() {
    var fixture = seed(AuctionInspectionStatus.NORMAL);
    var other = seed(AuctionInspectionStatus.NORMAL);
    long before = proceeds.count();
    assertThatThrownBy(
            () ->
                service.record(other.houseId(), "다른 경매장", 1000L, 930L, List.of(fixture.resultId())))
        .isInstanceOf(ConflictException.class);
    assertThat(proceeds.count()).isEqualTo(before);
  }

  private Fixture seed(AuctionInspectionStatus inspection) {
    var house =
        partners.saveAndFlush(
            new BusinessPartner(
                "자료 " + UUID.randomUUID(), PartnerType.AUCTION_HOUSE, null, null, null, null));
    var date = LocalDate.of(2026, 10, 7);
    var shipment = new AuctionShipment(date, house.getId(), PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("출하 품목", "품종", null, null, 1);
    var attempt = new AuctionAttempt(date, 1, AuctionAttemptStatus.SOLD, null, null);
    var line = new AuctionResultLine(date, null, 1, 1000, 1000, null, inspection);
    attempt.addResultLine(line);
    lot.addAttempt(attempt);
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    return new Fixture(house.getId(), line.getId());
  }

  private record Fixture(Long houseId, Long resultId) {}
}

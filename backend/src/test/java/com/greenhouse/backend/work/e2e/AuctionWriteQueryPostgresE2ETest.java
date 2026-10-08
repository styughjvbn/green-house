package com.greenhouse.backend.work.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.application.auction.AuctionTrackingService;
import com.greenhouse.backend.sales.application.auction.RecordAuctionResultCommand;
import com.greenhouse.backend.sales.domain.auction.AuctionAttempt;
import com.greenhouse.backend.sales.domain.auction.AuctionAttemptStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionInspectionStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionLotStatus;
import com.greenhouse.backend.sales.domain.auction.AuctionResultLine;
import com.greenhouse.backend.sales.domain.auction.AuctionShipment;
import com.greenhouse.backend.sales.domain.auction.AuctionShipmentLot;
import com.greenhouse.backend.sales.dto.auction.AuctionLotAdjustmentRequest;
import com.greenhouse.backend.sales.dto.auction.AuctionLotResponse;
import com.greenhouse.backend.sales.dto.auction.AuctionLotReturnRequest;
import com.greenhouse.backend.sales.dto.auction.AuctionLotStatusRequest;
import com.greenhouse.backend.sales.partner.domain.BusinessPartner;
import com.greenhouse.backend.sales.partner.repository.BusinessPartnerRepository;
import com.greenhouse.backend.sales.repository.auction.AuctionShipmentRepository;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

@Tag("work-e2e")
class AuctionWriteQueryPostgresE2ETest extends WorkE2ETestBase {
  private static final LocalDate DATE = LocalDate.of(2026, 7, 1);
  @Autowired AuctionTrackingService auctions;
  @Autowired AuctionShipmentRepository shipments;
  @Autowired BusinessPartnerRepository partners;
  @Autowired EntityManagerFactory emf;

  @ParameterizedTest
  @CsvSource({
    "STATUS,1", "STATUS,10", "STATUS,50",
    "RESULT,1", "RESULT,10", "RESULT,50",
    "RETURN,1", "RETURN,10", "RETURN,50",
    "ADJUST_NOOP,1", "ADJUST_NOOP,10", "ADJUST_NOOP,50"
  })
  void writeResponsesKeepEveryHistoryAndUseBoundedQueries(String action, int attemptCount)
      throws Exception {
    var partner =
        partners.saveAndFlush(
            new BusinessPartner(
                "경매 쓰기 " + UUID.randomUUID(), PartnerType.AUCTION_HOUSE, null, null, null, null));
    var shipment = new AuctionShipment(DATE, partner.getId(), PartnerType.AUCTION_HOUSE);
    var lot = new AuctionShipmentLot("난", "품종", "A", 1, 100);
    for (int i = 0; i < attemptCount; i++) {
      var attempt =
          new AuctionAttempt(DATE.plusDays(i), i + 1, AuctionAttemptStatus.FAILED, "유찰", null);
      attempt.addResultLine(
          new AuctionResultLine(
              DATE.plusDays(i), "A", 60, 0, 0, "첫 행", AuctionInspectionStatus.NORMAL));
      attempt.addResultLine(
          new AuctionResultLine(
              DATE.plusDays(i), "B", 40, 0, 0, "둘째 행", AuctionInspectionStatus.MANUAL_REVIEW));
      lot.addAttempt(attempt);
      lot.changeStatus(
          i % 2 == 0 ? AuctionLotStatus.REAUCTION_WAITING : AuctionLotStatus.IN_PROGRESS,
          "이력 " + i,
          "기존 담당",
          null,
          LocalDateTime.of(2026, 7, 1, 0, 0).plusMinutes(i));
    }
    lot.changeStatus(
        AuctionLotStatus.REAUCTION_WAITING,
        "반환 대기",
        "기존 담당",
        null,
        LocalDateTime.of(2026, 7, 2, 0, 0));
    shipment.addLot(lot);
    shipments.saveAndFlush(shipment);
    var attemptIds = lot.getAttempts().stream().map(AuctionAttempt::getId).toList();
    var historyIds = lot.getStatusHistory().stream().map(history -> history.getId()).toList();
    var resultIds =
        lot.getAttempts().stream()
            .map(attempt -> attempt.getResultLines().stream().map(line -> line.getId()).toList())
            .toList();
    var command =
        new RecordAuctionResultCommand(
            "new-result",
            DATE.minusDays(1),
            null,
            AuctionAttemptStatus.FAILED,
            "추가 유찰",
            null,
            List.of());
    var returned = new AuctionLotReturnRequest("new-return", 10, DATE.plusDays(60), "반환 담당", "반환");
    var stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    // Each service call owns and commits its transaction; fixture Entities are detached.
    AuctionLotResponse response =
        switch (action) {
          case "STATUS" ->
              auctions.changeStatus(
                  lot.getId(),
                  new AuctionLotStatusRequest(AuctionLotStatus.IN_PROGRESS, "상태 변경", "담당", null));
          case "RESULT" -> auctions.addResult(lot.getId(), command);
          case "RETURN" -> auctions.confirmReturn(lot.getId(), returned);
          case "ADJUST_NOOP" ->
              auctions.adjust(lot.getId(), new AuctionLotAdjustmentRequest(0, 100, 0, "담당", null));
          default -> throw new AssertionError(action);
        };
    long statements = stats.getPrepareStatementCount();
    var path =
        Path.of("build/work-query-count/auction-write-" + action + "-" + attemptCount + ".json");
    Files.createDirectories(path.getParent());
    objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValue(
            path.toFile(),
            Map.of(
                "preparedStatements",
                statements,
                "entityLoads",
                stats.getEntityLoadCount(),
                "collectionFetches",
                stats.getCollectionFetchCount()));
    assertThat(response.attempts().subList(0, attemptCount))
        .extracting(attempt -> attempt.id())
        .containsExactlyElementsOf(attemptIds);
    for (int i = 0; i < attemptCount; i++)
      assertThat(response.attempts().get(i).resultLines())
          .extracting(line -> line.id())
          .containsExactlyElementsOf(resultIds.get(i));
    assertThat(response.statusHistory().subList(0, historyIds.size()))
        .extracting(history -> history.id())
        .containsExactlyElementsOf(historyIds);
    assertThat(response.inspectionStatus()).isEqualTo(AuctionInspectionStatus.MANUAL_REVIEW);
    assertThat(response.quantityAdjustmentAllowed()).isFalse();
    if (action.equals("RESULT")) {
      assertThat(response.attempts()).hasSize(attemptCount + 1);
      assertThat(response.attempts().getLast().auctionDate()).isEqualTo(DATE.minusDays(1));
      assertThat(response.attempts().getLast().id()).isPositive();
      assertThat(auctions.addResult(lot.getId(), command)).isEqualTo(response);
    }
    if (action.equals("RETURN")) {
      assertThat(response.returnedQuantity()).isEqualTo(10);
      assertThat(response.statusHistory().getLast().id()).isPositive();
      assertThat(auctions.confirmReturn(lot.getId(), returned)).isEqualTo(response);
    }
    assertThat(statements)
        .as("%s with %s attempts (including commit)", action, attemptCount)
        .isLessThanOrEqualTo(14);
  }
}

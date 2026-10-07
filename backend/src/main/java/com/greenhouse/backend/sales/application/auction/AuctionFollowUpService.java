package com.greenhouse.backend.sales.application.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.audit.domain.AuditSource;
import com.greenhouse.backend.common.api.PageRequests;
import com.greenhouse.backend.common.api.PageResponse;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.application.orchid.mutation.CompensateCreateMutationsCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationResult;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationSources;
import com.greenhouse.backend.sales.domain.auction.*;
import com.greenhouse.backend.sales.dto.auction.AuctionArrivalResponse;
import com.greenhouse.backend.sales.dto.auction.AuctionFollowUpResponse;
import com.greenhouse.backend.sales.dto.auction.AuctionFollowUpResult;
import com.greenhouse.backend.sales.repository.auction.*;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionFollowUpService {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final AuctionShipmentLotRepository lots;
  private final AuctionAttemptRepository attempts;
  private final AuctionFollowUpDecisionRepository decisions;
  private final AuctionReturnArrivalRepository arrivals;
  private final AuctionCommandReceiptRepository receipts;
  private final OrchidGroupMutationEngine farm;
  private final RequestActorProvider actors;
  private final AuditEventWriter audit;
  private final Clock clock;

  public AuctionFollowUpResponse getFollowUp(Long lotId) {
    var lot = lots.findWithDetailsById(lotId).orElseThrow(() -> missingLot());
    return summary(lot);
  }

  public PageResponse<AuctionArrivalResponse> getArrivals(Long lotId, int page, int size) {
    PageRequests.validate(page, size);
    if (!lots.existsById(lotId)) throw missingLot();
    return PageResponse.from(
        arrivals
            .findByLotId(lotId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
            .map(AuctionArrivalResponse::from));
  }

  @Transactional
  public AuctionFollowUpResult decide(Long lotId, AuctionFollowUpCommand command) {
    var lot = lock(lotId);
    String fingerprint = fingerprint(command.idempotencyKey(), command);
    var replay = replay(lotId, "FOLLOW_UP", command.idempotencyKey(), fingerprint);
    if (replay.isPresent()) return replay.get();
    attempts.findAllWithResultLinesByLotIdIn(List.of(lotId));
    var before = summary(lot);
    String worker = actors.resolve(command.worker());
    int quantity =
        lot.decideFollowUp(
            command.method(),
            arrivals.existsByLotIdAndCanceledAtIsNull(lotId),
            worker,
            command.reason(),
            TimeConfig.utcNow(clock));
    decisions.saveAndFlush(
        new AuctionFollowUpDecision(
            lotId,
            command.method(),
            quantity,
            worker,
            command.reason().trim(),
            TimeConfig.utcNow(clock)));
    var response = new AuctionFollowUpResult(summary(lot), null);
    audit.record(
        AuditAction.UPDATED,
        AuditSource.SALES_MANAGEMENT,
        "AUCTION_LOT",
        lotId,
        Map.of("followUp", before),
        Map.of("followUp", response.followUp()),
        Map.of("operation", "FOLLOW_UP"));
    return record(lotId, "FOLLOW_UP", command.idempotencyKey(), fingerprint, response);
  }

  @Transactional
  public AuctionFollowUpResult arrive(Long lotId, AuctionArrivalCommand command) {
    var lot = lock(lotId);
    String fingerprint = fingerprint(command.idempotencyKey(), command);
    var replay = replay(lotId, "ARRIVAL", command.idempotencyKey(), fingerprint);
    if (replay.isPresent()) return replay.get();
    var decision =
        decisions
            .findFirstByLotIdOrderByIdDesc(lotId)
            .orElseThrow(() -> new IllegalArgumentException("농장 반환 결정을 먼저 확인해야 합니다."));
    int quantity = command.details().quantity();
    String worker = actors.resolve(command.worker());
    var before = summary(lot);
    lot.recordActualArrival(quantity, command.arrivalDate(), worker, TimeConfig.utcNow(clock));
    var arrival =
        arrivals.saveAndFlush(
            new AuctionReturnArrival(
                lotId,
                decision.getId(),
                quantity,
                command.arrivalDate(),
                worker,
                TimeConfig.utcNow(clock)));
    var creation =
        farm.create(
            new CreateOrchidGroupMutationCommand(
                OrchidGroupMutationSources.auctionReturnArrival(arrival.getId(), "CREATE"),
                command.bedZoneId(),
                command.details(),
                command.arrivalDate(),
                "경매 반환품 실제 도착"));
    arrival.linkCreation(creation.entries().getFirst().orchidGroupId(), creation.mutationId());
    arrivals.flush();
    var response = new AuctionFollowUpResult(summary(lot), AuctionArrivalResponse.from(arrival));
    audit.record(
        AuditAction.CREATED,
        AuditSource.SALES_MANAGEMENT,
        "AUCTION_RETURN_ARRIVAL",
        arrival.getId(),
        Map.of("followUp", before),
        Map.of("arrival", response.arrival(), "followUp", response.followUp()),
        Map.of("lotId", lotId, "decisionId", decision.getId()));
    return record(lotId, "ARRIVAL", command.idempotencyKey(), fingerprint, response);
  }

  @Transactional
  public AuctionFollowUpResult cancelArrival(
      Long lotId, Long arrivalId, CancelAuctionArrivalCommand command) {
    var lot = lock(lotId);
    String fingerprint =
        fingerprint(command.idempotencyKey(), Map.of("arrivalId", arrivalId, "command", command));
    var replay = replay(lotId, "ARRIVAL_CANCEL", command.idempotencyKey(), fingerprint);
    if (replay.isPresent()) return replay.get();
    var arrival =
        arrivals
            .findByIdAndLotId(arrivalId, lotId)
            .orElseThrow(() -> new NotFoundException("실제 도착 기록을 찾을 수 없습니다."));
    if (arrival.getCanceledAt() != null)
      throw new ConflictException("AUCTION_ARRIVAL_ALREADY_CANCELED", "이미 취소한 도착 기록입니다.");
    var before = AuctionArrivalResponse.from(arrival);
    OrchidGroupMutationResult compensation;
    try {
      compensation =
          farm.compensateCreations(
              new CompensateCreateMutationsCommand(
                  OrchidGroupMutationSources.auctionReturnArrival(arrivalId, "CANCEL"),
                  List.of(arrival.getCreationMutationId()),
                  command.correctionDate(),
                  command.reason()));
    } catch (IllegalArgumentException failure) {
      var conflict =
          new ConflictException(
              "AUCTION_ARRIVAL_COMPENSATION_BLOCKED", "후속 변경이나 사용을 먼저 정정한 뒤 도착 기록을 취소해야 합니다.");
      conflict.initCause(failure);
      throw conflict;
    }
    arrival.cancel(compensation.mutationId(), command.reason(), TimeConfig.utcNow(clock));
    arrivals.flush();
    lot.cancelActualArrival(
        arrival.getQuantity(),
        arrivals.latestValidArrivalDate(lotId),
        actors.resolve(command.worker()),
        command.reason().trim(),
        TimeConfig.utcNow(clock));
    var response = new AuctionFollowUpResult(summary(lot), AuctionArrivalResponse.from(arrival));
    audit.record(
        AuditAction.UPDATED,
        AuditSource.SALES_MANAGEMENT,
        "AUCTION_RETURN_ARRIVAL",
        arrivalId,
        Map.of("arrival", before),
        Map.of("arrival", response.arrival()),
        Map.of("lotId", lotId, "operation", "CANCEL"));
    return record(lotId, "ARRIVAL_CANCEL", command.idempotencyKey(), fingerprint, response);
  }

  private AuctionFollowUpResponse summary(AuctionShipmentLot lot) {
    var decision = decisions.findFirstByLotIdOrderByIdDesc(lot.getId()).orElse(null);
    boolean selectable =
        lot.isFollowUpDecisionChangeAllowed(arrivals.existsByLotIdAndCanceledAtIsNull(lot.getId()));
    return new AuctionFollowUpResponse(
        lot.getId(),
        decision == null ? null : decision.getId(),
        lot.getFollowUpMethod(),
        decision == null ? null : decision.getQuantity(),
        lot.getWaitingQuantity(),
        lot.getDisposedQuantity(),
        lot.getInferredReturnQuantity(),
        selectable,
        lot.isActualArrivalAllowed(),
        selectable ? List.of(AuctionFollowUpMethod.values()) : List.of());
  }

  private AuctionShipmentLot lock(Long id) {
    return lots.findForUpdateById(id).orElseThrow(() -> missingLot());
  }

  private NotFoundException missingLot() {
    return new NotFoundException("경매 출하 lot를 찾을 수 없습니다.");
  }

  private String fingerprint(String key, Object value) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new IllegalArgumentException("경매 요청 키는 1~100자의 공백이 아닌 값이 필요합니다.");
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(MAPPER.writeValueAsBytes(value)));
    } catch (Exception failure) {
      throw new IllegalStateException("경매 요청 지문 생성에 실패했습니다.", failure);
    }
  }

  private Optional<AuctionFollowUpResult> replay(
      Long lotId, String type, String key, String fingerprint) {
    return receipts
        .findByLotIdAndCommandTypeAndRequestKey(lotId, type, key)
        .map(
            receipt -> {
              receipt.validate(fingerprint);
              try {
                return MAPPER.readValue(receipt.getResponseSnapshot(), AuctionFollowUpResult.class);
              } catch (Exception failure) {
                throw new IllegalStateException("경매 요청의 저장된 응답을 읽을 수 없습니다.", failure);
              }
            });
  }

  private AuctionFollowUpResult record(
      Long lotId, String type, String key, String fingerprint, AuctionFollowUpResult response) {
    lots.flush();
    try {
      receipts.saveAndFlush(
          new AuctionCommandReceipt(
              lotId,
              type,
              key,
              fingerprint,
              MAPPER.writeValueAsString(response),
              TimeConfig.utcNow(clock)));
      return response;
    } catch (JsonProcessingException failure) {
      throw new IllegalStateException("경매 요청의 응답을 저장할 수 없습니다.", failure);
    }
  }
}

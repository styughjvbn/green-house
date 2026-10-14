package com.greenhouse.backend.sales.direct.application;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.application.AuditEventWriter;
import com.greenhouse.backend.audit.domain.*;
import com.greenhouse.backend.common.api.*;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.*;
import com.greenhouse.backend.farm.api.orchid.mutation.*;
import com.greenhouse.backend.sales.direct.domain.*;
import com.greenhouse.backend.sales.direct.repository.*;
import com.greenhouse.backend.sales.direct.web.dto.*;
import com.greenhouse.backend.sales.document.api.DirectDocumentChangeApi;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectReturnArrivalService {
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final DirectDocumentChangeApi documents;
  private final DirectSaleRepository sales;
  private final DirectSaleChangeRepository changes;
  private final DirectReturnArrivalRepository arrivals;
  private final DirectReturnCommandReceiptRepository receipts;
  private final OrchidGroupMutationWriter farm;
  private final RequestActorProvider actors;
  private final AuditEventWriter audit;
  private final Clock clock;

  public DirectReturnArrivalState get(Long id, int page, int size) {
    PageRequests.validate(page, size);
    var target = documents.find(id);
    var history = changes.findByDocumentIdInOrderByIdAsc(List.of(id));
    var canceled = canceledReturns(history);
    var arrived = arrivedQuantities(id);
    var names = target.items().stream().collect(Collectors.toMap(i -> i.id(), i -> i.name()));
    var pending =
        history.stream()
            .filter(
                c -> c.getType() == DirectSaleChangeType.RETURN && !canceled.contains(c.getId()))
            .map(
                c -> {
                  int remaining =
                      DirectReturnArrivalPolicy.pendingQuantity(
                          c, arrived.getOrDefault(c.getId(), 0));
                  return new DirectReturnArrivalState.PendingReturn(
                      c.getId(),
                      c.getItemId(),
                      names.get(c.getItemId()),
                      remaining,
                      target.changeAllowed() && remaining > 0);
                })
            .toList();
    return new DirectReturnArrivalState(
        pending,
        PageResponse.from(
            arrivals
                .findByDocumentId(
                    id, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(DirectReturnArrivalResponse::from)));
  }

  @Transactional
  public DirectReturnArrivalResult arrive(
      Long id, Long returnId, DirectReturnArrivalRequest request, String key) {
    var target = lock(id);
    String fingerprint = fingerprint("ARRIVAL:" + returnId, request, key);
    var replay = replay(id, key, fingerprint);
    if (replay.isPresent()) return replay.get();
    requireCompleted(target);
    request.validateInventoryInput();
    var history = changes.findByDocumentIdInOrderByIdAsc(List.of(id));
    var original =
        history.stream()
            .filter(c -> c.getId().equals(returnId) && c.getType() == DirectSaleChangeType.RETURN)
            .findFirst()
            .orElseThrow(() -> new NotFoundException("반품 기록을 찾을 수 없습니다."));
    DirectReturnArrivalPolicy.requireArrival(
        original,
        canceledReturns(history).contains(returnId),
        arrivedQuantities(id).getOrDefault(returnId, 0),
        request.quantity(),
        request.arrivalDate());
    var arrival =
        arrivals.saveAndFlush(
            new DirectReturnArrival(
                id,
                returnId,
                request.quantity(),
                request.arrivalDate(),
                actors.resolve(request.worker()),
                TimeConfig.utcNow(clock)));
    if (!request.recordOnly()) {
      var result =
          farm.create(
              new CreateOrchidGroupMutationCommand(
                  OrchidGroupMutationSources.directReturnArrival(arrival.getId(), "CREATE"),
                  request.bedZoneId(),
                  request.details(),
                  request.arrivalDate(),
                  "일반 판매 반품 실제 도착"));
      arrival.linkCreation(result.entries().getFirst().orchidGroupId(), result.mutationId());
    }
    return finish(id, key, fingerprint, arrival, "ARRIVAL");
  }

  @Transactional
  public DirectReturnArrivalResult cancel(
      Long id, Long arrivalId, CancelDirectReturnArrivalRequest request, String key) {
    var target = lock(id);
    String fingerprint = fingerprint("CANCEL:" + arrivalId, request, key);
    var replay = replay(id, key, fingerprint);
    if (replay.isPresent()) return replay.get();
    requireCompleted(target);
    var arrival =
        arrivals
            .findByIdAndDocumentId(arrivalId, id)
            .orElseThrow(() -> new NotFoundException("반품 도착 기록을 찾을 수 없습니다."));
    if (!arrival.isCancellationAllowed())
      throw new ConflictException("DIRECT_ARRIVAL_ALREADY_CANCELED", "이미 취소한 도착 기록입니다.");
    if (request.correctionDate().isBefore(arrival.getArrivalDate()))
      throw new IllegalArgumentException("정정일은 도착일 이후여야 합니다.");
    Long compensationId = null;
    if (arrival.getCreationMutationId() != null) {
      try {
        compensationId =
            farm.compensateCreations(
                    new CompensateCreateMutationsCommand(
                        OrchidGroupMutationSources.directReturnArrival(arrivalId, "CANCEL"),
                            List.of(arrival.getCreationMutationId()),
                        request.correctionDate(), request.reason()))
                .mutationId();
      } catch (IllegalArgumentException cause) {
        var conflict =
            new ConflictException(
                "DIRECT_ARRIVAL_COMPENSATION_BLOCKED", "후속 사용을 정정한 뒤 도착 기록을 취소해야 합니다.");
        conflict.initCause(cause);
        throw conflict;
      }
    }
    arrival.cancel(
        compensationId, request.correctionDate(), request.reason(), TimeConfig.utcNow(clock));
    return finish(id, key, fingerprint, arrival, "CANCEL");
  }

  private DirectDocumentChangeApi.Target lock(Long id) {
    var target = documents.lock(id);
    sales.findForUpdate(id).orElseThrow(() -> new NotFoundException("일반 판매를 찾을 수 없습니다."));
    return target;
  }

  private void requireCompleted(DirectDocumentChangeApi.Target target) {
    if (!target.changeAllowed())
      throw new ConflictException("DIRECT_CHANGE_COMPLETED_REQUIRED", "출고 완료된 일반 판매에서 처리해야 합니다.");
  }

  private Set<Long> canceledReturns(List<DirectSaleChange> history) {
    return history.stream()
        .filter(c -> c.getType() == DirectSaleChangeType.CANCEL)
        .map(DirectSaleChange::getCanceledChangeId)
        .collect(Collectors.toSet());
  }

  private Map<Long, Integer> arrivedQuantities(Long id) {
    return arrivals.findByDocumentIdAndCanceledAtIsNull(id).stream()
        .collect(
            Collectors.toMap(
                DirectReturnArrival::getReturnChangeId,
                DirectReturnArrival::getQuantity,
                Math::addExact));
  }

  private Optional<DirectReturnArrivalResult> replay(Long id, String key, String fingerprint) {
    return receipts
        .findByDocumentIdAndRequestKey(id, key)
        .map(
            r -> {
              if (!r.getFingerprint().equals(fingerprint))
                throw new ConflictException(
                    "DIRECT_ARRIVAL_REQUEST_KEY_CONFLICT", "같은 요청 키의 입력이 다릅니다.");
              return new DirectReturnArrivalResult(r.getArrivalId());
            });
  }

  private DirectReturnArrivalResult finish(
      Long id, String key, String fingerprint, DirectReturnArrival arrival, String operation) {
    arrivals.flush();
    audit.record(
        AuditAction.UPDATED,
        AuditSource.SALES_MANAGEMENT,
        "DIRECT_RETURN_ARRIVAL",
        arrival.getId(),
        null,
        Map.of("arrival", DirectReturnArrivalResponse.from(arrival)),
        Map.of("operation", operation, "documentId", id));
    receipts.saveAndFlush(
        new DirectReturnCommandReceipt(
            id, key, fingerprint, arrival.getId(), TimeConfig.utcNow(clock)));
    return new DirectReturnArrivalResult(arrival.getId());
  }

  private String fingerprint(String operation, Object request, String key) {
    if (key == null || key.isBlank() || key.length() > 100)
      throw new IllegalArgumentException("요청 키는 1~100자가 필요합니다.");
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(MAPPER.writeValueAsBytes(List.of(operation, request))));
    } catch (Exception cause) {
      throw new IllegalStateException("도착 요청 지문 생성 실패", cause);
    }
  }
}

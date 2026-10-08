package com.greenhouse.backend.sales.application.payment;

import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.sales.domain.payment.*;
import com.greenhouse.backend.sales.dto.payment.*;
import com.greenhouse.backend.sales.partner.api.BusinessPartnerLockApi;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import com.greenhouse.backend.sales.repository.payment.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Partner -> documents -> Direct sources -> auction proceeds -> cash roots, all in one transaction.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAllocationService {
  private final BusinessPartnerLockApi partners;
  private final PartnerPaymentEventRepository events;
  private final PaymentAllocationCommandReceiptRepository requests;
  private final PaymentReceiptIntegrity cash;
  private final PartnerBalanceService balances;
  private final List<PaymentAllocationTargetPort<?>> targets;
  private final PaymentAuditSupport audit;
  private final RequestActorProvider actors;
  private final Clock clock;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  public PaymentAllocationChangeResponse allocate(
      Long partnerId, PaymentAllocationRequest command) {
    return change(
        partnerId,
        command.idempotencyKey(),
        fingerprint("ALLOCATE", command),
        command.allocationDate(),
        List.of(),
        command.allocations(),
        null,
        command.worker());
  }

  public PaymentAllocationChangeResponse correct(
      Long partnerId, PaymentAllocationCorrectionRequest command) {
    return change(
        partnerId,
        command.idempotencyKey(),
        fingerprint("CORRECT", command),
        command.correctionDate(),
        command.cancellationIds(),
        command.allocations(),
        command.reason(),
        command.worker());
  }

  private PaymentAllocationChangeResponse change(
      Long partnerId,
      String key,
      String fingerprint,
      LocalDate date,
      List<Long> cancellationIds,
      List<PaymentAllocationLine> additions,
      String reason,
      String worker) {
    partners.lockAll(List.of(partnerId));
    key = key.trim();
    var replay = requests.findByPartnerIdAndRequestKey(partnerId, key);
    if (replay.isPresent()) {
      var result = replay.orElseThrow().replay(fingerprint);
      return new PaymentAllocationChangeResponse(
          result.get("allocations"), result.get("cancellations"));
    }
    if (date == null
        || cancellationIds.size() > 100
        || additions.size() > 100
        || (cancellationIds.isEmpty() && additions.isEmpty()))
      throw new IllegalArgumentException("배분 또는 정정 대상은 1~100개여야 합니다.");
    if (new HashSet<>(cancellationIds).size() != cancellationIds.size())
      throw new IllegalArgumentException("같은 배분을 중복 취소할 수 없습니다.");
    additions.forEach(
        line -> PartnerPaymentEvent.requireAllocationTarget(line.targetType(), line.targetId()));
    var cancellations =
        cancellationIds.isEmpty()
            ? List.<PartnerPaymentEvent>of()
            : events.findAllOwned(partnerId, cancellationIds);
    if (cancellations.size() != cancellationIds.size())
      throw new NotFoundException("거래처의 배분을 모두 찾을 수 없습니다.");
    for (var allocation : cancellations) {
      if (!allocation.isAllocation()
          || allocation.getStatus() != PaymentEventStatus.CONFIRMED
          || allocation.getParentEvent() == null)
        throw new ConflictException(
            "PAYMENT_ALLOCATION_CANCELLATION_BLOCKED", "유효한 배분만 취소할 수 있습니다.");
      PartnerPaymentEvent.requireAllocationTarget(
          allocation.getTargetType(), allocation.getTargetId());
    }
    var references = new TreeSet<Target>();
    additions.forEach(line -> references.add(new Target(line.targetType(), line.targetId())));
    cancellations.forEach(
        event -> references.add(new Target(event.getTargetType(), event.getTargetId())));
    for (PaymentTargetType type :
        List.of(PaymentTargetType.SALES_SLIP, PaymentTargetType.AUCTION_PROCEEDS)) {
      var ids = references.stream().filter(target -> target.type == type).map(Target::id).toList();
      for (Long id : ids) port(type).lockAllocationTarget(id, partnerId);
      if (!ids.isEmpty()) port(type).lockAllocationAmounts(ids);
    }
    var receiptIds = new TreeSet<Long>();
    additions.forEach(line -> receiptIds.add(line.receiptId()));
    cancellations.forEach(event -> receiptIds.add(event.getParentEvent().getId()));
    var roots = events.findAllForUpdate(partnerId, receiptIds);
    if (roots.size() != receiptIds.size()) throw new NotFoundException("거래처의 수납을 모두 찾을 수 없습니다.");
    var rootMap =
        roots.stream().collect(Collectors.toMap(PartnerPaymentEvent::getId, value -> value));
    var initial = cash.findAll(receiptIds);
    for (var root : roots) requireCash(root, initial.get(root.getId()));
    var before = new LinkedHashMap<Target, Map<String, Object>>();
    references.forEach(target -> before.put(target, port(target.type).paymentSnapshot(target.id)));
    String actor = actors.resolve(worker);
    if (actor == null) actor = "관리자";
    var canceled = new ArrayList<Long>();
    for (var allocation :
        cancellations.stream().sorted(Comparator.comparing(PartnerPaymentEvent::getId)).toList()) {
      var correction = events.save(allocation.cancelAllocation(date, reason, actor));
      canceled.add(correction.getId());
      audit.recordManualPayment(correction);
    }
    events.flush();
    // Capacity is checked after canceled money is restored, before any new allocation is written.
    var totals = new TreeMap<Target, Long>();
    for (var line : additions) {
      if (line.amount() == null || line.amount() <= 0)
        throw new IllegalArgumentException("배분액은 양수여야 합니다.");
      try {
        totals.merge(new Target(line.targetType(), line.targetId()), line.amount(), Math::addExact);
      } catch (ArithmeticException overflow) {
        throw new IllegalArgumentException("배분 합계가 금액 저장 한도를 초과합니다.", overflow);
      }
    }
    for (var entry : totals.entrySet())
      port(entry.getKey().type)
          .recordPayment(entry.getKey().id, entry.getValue(), actor, TimeConfig.utcNow(clock));
    var allocated = new ArrayList<Long>();
    for (int index = 0; index < additions.size(); index++) {
      var line = additions.get(index);
      var allocation =
          events.save(
              PartnerPaymentEvent.allocated(
                  rootMap.get(line.receiptId()),
                  line.targetType(),
                  line.targetId(),
                  line.amount(),
                  date,
                  actor,
                  "ALLOCATION:" + partnerId + ":" + key + ":" + index));
      allocated.add(allocation.getId());
      audit.recordManualPayment(allocation);
    }
    events.flush();
    var current = cash.findAll(receiptIds);
    for (var root : roots) requireCash(root, current.get(root.getId()));
    Long last = allocated.isEmpty() ? canceled.getLast() : allocated.getLast();
    for (var target : references) {
      port(target.type).updateBalance(target.id, last);
      port(target.type).auditPayment(target.id, before.get(target));
    }
    balances.refreshUnassignedAmount(partnerId, last);
    var result = new PaymentAllocationChangeResponse(List.copyOf(allocated), List.copyOf(canceled));
    requests.save(
        new PaymentAllocationCommandReceipt(
            partnerId, key, fingerprint, result.allocationIds(), result.cancellationIds()));
    return result;
  }

  private void requireCash(PartnerPaymentEvent root, PaymentReceiptQueryRepository.State state) {
    if (state == null || !root.isReceiptUsable(state.reviewRequired()))
      throw new ConflictException(
          "PAYMENT_RECEIPT_REVIEW_REQUIRED", "수납과 유효 배분의 대사가 필요합니다. 검토 전에는 배분을 변경할 수 없습니다.");
  }

  private PaymentAllocationTargetPort<?> port(PaymentTargetType type) {
    return targets.stream()
        .filter(port -> port.targetType() == type)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 배분 대상입니다."));
  }

  private record Target(PaymentTargetType type, Long id) implements Comparable<Target> {
    public int compareTo(Target other) {
      int order = type.compareTo(other.type);
      return order == 0 ? id.compareTo(other.id) : order;
    }
  }

  private String fingerprint(String operation, Object command) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      (operation + ":" + MAPPER.writeValueAsString(command))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (Exception failure) {
      throw new IllegalStateException("배분 요청의 지문을 계산할 수 없습니다.", failure);
    }
  }
}

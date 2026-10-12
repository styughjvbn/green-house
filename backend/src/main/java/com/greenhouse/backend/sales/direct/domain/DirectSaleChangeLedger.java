package com.greenhouse.backend.sales.direct.domain;

import com.greenhouse.backend.common.exception.ConflictException;
import java.util.*;

/** Rebuilds price portions from original prices and immutable, ordered commercial changes. */
public final class DirectSaleChangeLedger {
  public record Portion(Long itemId, Long sourceChangeId, int quantity, int unitPrice) {}

  private record Key(Long itemId, Long sourceId) {}

  private final Map<Key, Portion> portions = new LinkedHashMap<>();
  private final Map<Long, DirectSaleChange> applied = new LinkedHashMap<>();
  private final Set<Long> canceled = new HashSet<>();
  private long total;
  private long allocationCeiling;

  public DirectSaleChangeLedger(List<DirectSalePrice> prices, List<DirectSaleChange> changes) {
    for (var price : prices) {
      total = Math.addExact(total, price.getAmount());
      portions.put(
          new Key(price.getDocumentItemId(), null),
          new Portion(
              price.getDocumentItemId(), null, price.getPricedQuantity(), price.getUnitPrice()));
    }
    for (var change : changes) {
      allocationCeiling = Math.max(allocationCeiling, change.getAllocatedBefore());
      if (change.getType() == DirectSaleChangeType.CANCEL) {
        var original = applied.get(change.getCanceledChangeId());
        if (original == null
            || !canCancel(original.getId())
            || change.getAmountDelta() != -original.getAmountDelta()
            || !original.getItemId().equals(change.getItemId())) fail();
        if (original.getType() == DirectSaleChangeType.PRICE_ADJUSTMENT)
          portions.remove(new Key(original.getItemId(), original.getId()));
        add(original.getItemId(), original.getSourceChangeId(), original.getQuantity());
        canceled.add(original.getId());
      } else {
        var source = portion(change.getItemId(), change.getSourceChangeId());
        if (change.getQuantity() < 1
            || change.getQuantity() > source.quantity()
            || source.unitPrice() != change.getPreviousUnitPrice()
            || change.getNextUnitPrice() < 0) fail();
        long expected =
            change.getType() == DirectSaleChangeType.RETURN
                ? -(long) change.getQuantity() * source.unitPrice()
                : (long) change.getQuantity()
                    * (change.getNextUnitPrice() - (long) source.unitPrice());
        if (expected != change.getAmountDelta()) fail();
        add(source.itemId(), source.sourceChangeId(), -change.getQuantity());
        if (change.getType() == DirectSaleChangeType.PRICE_ADJUSTMENT)
          portions.put(
              new Key(change.getItemId(), change.getId()),
              new Portion(
                  change.getItemId(),
                  change.getId(),
                  change.getQuantity(),
                  change.getNextUnitPrice()));
      }
      total = Math.addExact(total, change.getAmountDelta());
      if (total < 0 || total > Integer.MAX_VALUE) fail();
      applied.put(change.getId(), change);
    }
    allocationCeiling = Math.max(total, allocationCeiling);
  }

  public record Plan(int previousUnitPrice, int nextUnitPrice, long delta, long totalAfter) {}

  public Plan plan(
      DirectSaleChangeType type, Long itemId, Long sourceId, int quantity, Integer unitPrice) {
    var source = portion(itemId, sourceId);
    if (quantity < 1 || quantity > source.quantity())
      throw new ConflictException("DIRECT_CHANGE_QUANTITY_EXCEEDED", "선택한 가격 구간의 남은 수량을 초과합니다.");
    int next;
    if (type == DirectSaleChangeType.RETURN) {
      if (unitPrice != null) throw new IllegalArgumentException("반품 금액은 선택한 가격 구간으로 계산합니다.");
      next = 0;
    } else if (type == DirectSaleChangeType.PRICE_ADJUSTMENT) {
      if (unitPrice == null || unitPrice < 0 || unitPrice == source.unitPrice())
        throw new IllegalArgumentException("변경할 단가를 입력해야 합니다.");
      next = unitPrice;
    } else throw new IllegalArgumentException("지원하지 않는 가격 변경 유형입니다.");
    long delta = (long) quantity * (next - (long) source.unitPrice());
    return new Plan(source.unitPrice(), next, delta, Math.addExact(total, delta));
  }

  public Plan cancellationPlan(Long id) {
    if (!canCancel(id))
      throw new ConflictException(
          "DIRECT_CHANGE_CANCEL_BLOCKED", "취소할 수 없는 이력이거나 후속 가격 조정·반품을 먼저 취소해야 합니다.");
    var original = applied.get(id);
    return new Plan(
        original.getPreviousUnitPrice(),
        original.getNextUnitPrice(),
        -original.getAmountDelta(),
        Math.subtractExact(total, original.getAmountDelta()));
  }

  public Portion portion(Long itemId, Long sourceId) {
    var value = portions.get(new Key(itemId, sourceId));
    if (value == null)
      throw new ConflictException("DIRECT_PRICE_PORTION_NOT_FOUND", "가격 구간을 다시 조회해 주세요.");
    return value;
  }

  private void add(Long itemId, Long sourceId, int quantity) {
    var current = portion(itemId, sourceId);
    portions.put(
        new Key(itemId, sourceId),
        new Portion(
            itemId, sourceId, Math.addExact(current.quantity(), quantity), current.unitPrice()));
  }

  public List<Portion> portions() {
    return portions.values().stream().filter(p -> p.quantity() > 0).toList();
  }

  /**
   * All active returns, independent of the history page, grouped by their original price portion.
   */
  public List<Portion> returnedPortions() {
    var returned = new LinkedHashMap<Key, Portion>();
    for (var change : applied.values()) {
      if (change.getType() != DirectSaleChangeType.RETURN || canceled.contains(change.getId()))
        continue;
      var key = new Key(change.getItemId(), change.getSourceChangeId());
      var previous = returned.get(key);
      int quantity =
          Math.addExact(previous == null ? 0 : previous.quantity(), change.getQuantity());
      returned.put(
          key,
          new Portion(
              change.getItemId(),
              change.getSourceChangeId(),
              quantity,
              change.getPreviousUnitPrice()));
    }
    return List.copyOf(returned.values());
  }

  public long total() {
    return total;
  }

  public long allocationCeiling() {
    return allocationCeiling;
  }

  public boolean isCanceled(Long id) {
    return canceled.contains(id);
  }

  public boolean canCancel(Long id) {
    var change = applied.get(id);
    if (change == null || change.getType() == DirectSaleChangeType.CANCEL || canceled.contains(id))
      return false;
    if (change.getType() == DirectSaleChangeType.RETURN) return true;
    var portion = portions.get(new Key(change.getItemId(), id));
    return portion != null && portion.quantity() == change.getQuantity();
  }

  private static void fail() {
    throw new ConflictException("DIRECT_CHANGE_LEDGER_INVALID", "가격 조정·반품 이력을 확인해야 합니다.");
  }
}

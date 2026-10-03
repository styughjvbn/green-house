package com.greenhouse.backend.work.application.effect;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MovementQuantityAllocator {

  private MovementQuantityAllocator() {}

  public static Map<Long, Integer> allocateMovedBySource(StructureChangeCommand request) {
    Map<Long, Integer> discardBySourceId = allocateDiscardBySource(request);
    Map<Long, Integer> movedBySourceId = new LinkedHashMap<>();
    request.sources().stream()
        .sorted(Comparator.comparing(StructureChangeSourceInput::sourceOrchidGroupId))
        .forEach(
            source ->
                movedBySourceId.put(
                    source.sourceOrchidGroupId(),
                    source.inputQuantity()
                        - discardBySourceId.getOrDefault(source.sourceOrchidGroupId(), 0)));
    return movedBySourceId;
  }

  public static Map<Long, Integer> allocateDiscardBySource(StructureChangeCommand request) {
    List<StructureChangeSourceInput> sources =
        request.sources().stream()
            .sorted(Comparator.comparing(StructureChangeSourceInput::sourceOrchidGroupId))
            .toList();
    Map<Long, Integer> inputBySourceId = new LinkedHashMap<>();
    for (var source : sources) {
      if (inputBySourceId.put(source.sourceOrchidGroupId(), source.inputQuantity()) != null) {
        throw new IllegalArgumentException("작업 원본 난 묶음은 중복될 수 없습니다.");
      }
    }
    int requestedMoved = request.results().stream().mapToInt(result -> result.quantity()).sum();
    int totalInput = inputBySourceId.values().stream().mapToInt(Integer::intValue).sum();
    if (requestedMoved > totalInput) {
      throw new IllegalArgumentException("자리 이동 결과 수량은 투입 수량보다 클 수 없습니다.");
    }

    int totalDiscard = totalInput - requestedMoved;
    Map<Long, Integer> discardBySourceId = new LinkedHashMap<>();
    Map<Long, Long> remainderBySourceId = new LinkedHashMap<>();
    int allocatedDiscard = 0;
    for (var entry : inputBySourceId.entrySet()) {
      long weightedDiscard = (long) totalDiscard * entry.getValue();
      int discard = totalInput == 0 ? 0 : (int) (weightedDiscard / totalInput);
      discardBySourceId.put(entry.getKey(), discard);
      remainderBySourceId.put(entry.getKey(), totalInput == 0 ? 0 : weightedDiscard % totalInput);
      allocatedDiscard += discard;
    }
    int remainingDiscard = totalDiscard - allocatedDiscard;
    remainderBySourceId.entrySet().stream()
        .sorted(
            Map.Entry.<Long, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(remainingDiscard)
        .forEach(entry -> discardBySourceId.compute(entry.getKey(), (key, value) -> value + 1));
    return discardBySourceId;
  }
}

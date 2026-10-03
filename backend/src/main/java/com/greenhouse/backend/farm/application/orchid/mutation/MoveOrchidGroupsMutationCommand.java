package com.greenhouse.backend.farm.application.orchid.mutation;

import static com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationCommandNormalizer.normalizeText;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public record MoveOrchidGroupsMutationCommand(
    OrchidGroupMutationSource source,
    List<MoveOrchidGroupMutationItem> items,
    LocalDate effectiveBusinessDate,
    String reason,
    Set<Long> placementExclusionOrchidGroupIds)
    implements OrchidGroupMutationCommand {

  public MoveOrchidGroupsMutationCommand(
      OrchidGroupMutationSource source,
      List<MoveOrchidGroupMutationItem> items,
      LocalDate effectiveBusinessDate,
      String reason) {
    this(source, items, effectiveBusinessDate, reason, Set.of());
  }

  public MoveOrchidGroupsMutationCommand {
    if (source == null || items == null || items.isEmpty() || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("일괄 이동 Mutation의 source, 이동 대상과 업무일이 필요합니다.");
    }
    items =
        items.stream()
            .sorted(Comparator.comparing(MoveOrchidGroupMutationItem::orchidGroupId))
            .toList();
    if (items.stream().map(MoveOrchidGroupMutationItem::orchidGroupId).distinct().count()
        != items.size()) {
      throw new IllegalArgumentException("일괄 이동 난 묶음은 중복될 수 없습니다.");
    }
    reason = normalizeText(reason);
    placementExclusionOrchidGroupIds =
        placementExclusionOrchidGroupIds == null
            ? Set.of()
            : Set.copyOf(placementExclusionOrchidGroupIds);
    if (placementExclusionOrchidGroupIds.stream().anyMatch(id -> id == null || id < 1)) {
      throw new IllegalArgumentException("배치 검사 제외 난 묶음 ID가 올바르지 않습니다.");
    }
  }
}

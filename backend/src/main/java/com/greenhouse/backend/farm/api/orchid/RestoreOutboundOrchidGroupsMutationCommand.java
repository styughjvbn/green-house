package com.greenhouse.backend.farm.api.orchid;

import static com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationCommandNormalizer.normalizeText;
import static com.greenhouse.backend.farm.api.orchid.OrchidGroupQuantityMutationCommandNormalizer.normalizeItems;

import java.time.LocalDate;
import java.util.List;

public record RestoreOutboundOrchidGroupsMutationCommand(
    OrchidGroupMutationSource source,
    List<OrchidGroupQuantityMutationItem> items,
    RelatedOrchidGroupMutations compensatedMutations,
    LocalDate effectiveBusinessDate,
    String reason)
    implements OrchidGroupMutationCommand {

  public RestoreOutboundOrchidGroupsMutationCommand {
    if (source == null || compensatedMutations == null || effectiveBusinessDate == null) {
      throw new IllegalArgumentException("출고 복구 Mutation의 source, 원인과 업무일이 필요합니다.");
    }
    items = normalizeItems(items, "출고 복구 Mutation");
    reason = normalizeText(reason);
  }
}

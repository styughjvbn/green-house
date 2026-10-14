package com.greenhouse.backend.farm.api.orchid.mutation;

public record CreateOrchidGroupMutationItem(Long bedZoneId, OrchidGroupMutationDetails details) {

  public CreateOrchidGroupMutationItem {
    if (bedZoneId == null || details == null) {
      throw new IllegalArgumentException("생성할 논리 구역과 상세 상태가 필요합니다.");
    }
  }
}

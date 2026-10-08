package com.greenhouse.backend.farm.orchid.web.dto;

import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.mutation.ledger.domain.OrchidGroupMutationRelationType;

public record OrchidGroupMutationRelationResponse(
    Long id,
    Long mutationId,
    Long relatedMutationId,
    OrchidGroupMutationRelationType relationType) {

  public static OrchidGroupMutationRelationResponse from(OrchidGroupMutationRelation relation) {
    return new OrchidGroupMutationRelationResponse(
        relation.getId(),
        relation.getMutation().getId(),
        relation.getRelatedMutation().getId(),
        relation.getRelationType());
  }
}

package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelation;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationRelationType;

public record OrchidGroupMutationRelationResponse(Long id, Long mutationId, Long relatedMutationId,
		OrchidGroupMutationRelationType relationType) {

	public static OrchidGroupMutationRelationResponse from(OrchidGroupMutationRelation relation) {
		return new OrchidGroupMutationRelationResponse(relation.getId(), relation.getMutation().getId(),
				relation.getRelatedMutation().getId(), relation.getRelationType());
	}
}

package com.greenhouse.backend.farm.dto;

import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.farm.domain.OrchidGroupCollection;
import com.greenhouse.backend.farm.domain.OrchidGroupCollectionStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrchidGroupCollectionResponse(
		Long id,
		String name,
		String description,
		String purpose,
		OrchidGroupCollectionStatus status,
		int orchidGroupCount,
		int totalQuantity,
		String createdBy,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		List<OrchidGroupCollectionMemberResponse> members) {

	public static OrchidGroupCollectionResponse from(
			OrchidGroupCollection collection,
			List<OrchidGroupCollectionMemberResponse> members) {
		return new OrchidGroupCollectionResponse(
				collection.getId(), collection.getName(), collection.getDescription(), collection.getPurpose(),
				collection.getStatus(), members.size(), members.stream().mapToInt(member -> Math.max(0, member.quantity())).sum(),
				collection.getCreatedBy(), TimeConfig.toFarmTime(collection.getCreatedAt()),
				TimeConfig.toFarmTime(collection.getUpdatedAt()), members);
	}
}

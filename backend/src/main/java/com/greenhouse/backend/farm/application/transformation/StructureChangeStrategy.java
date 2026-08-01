package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.dto.effect.StructureChangeExecutionRequest;

public interface StructureChangeStrategy {

	String supports();

	String workLabel();

	OrchidGroupLineageRelationType lineageType();

	default void validate(StructureChangeExecutionRequest request) {
		long totalInput = request.sources().stream().mapToLong(source -> source.inputQuantity()).sum();
		long totalResult = request.results().stream().mapToLong(result -> result.quantity()).sum();
		if (totalResult > totalInput) {
			throw new IllegalArgumentException(
					workLabel() + " 결과 수량은 투입 수량보다 클 수 없습니다.");
		}
	}
}

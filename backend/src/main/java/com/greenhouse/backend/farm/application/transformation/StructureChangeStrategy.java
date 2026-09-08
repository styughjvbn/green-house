package com.greenhouse.backend.farm.application.transformation;

import com.greenhouse.backend.farm.domain.transformation.OrchidGroupLineageRelationType;
import com.greenhouse.backend.work.application.effect.StructureChangeCommand;
import java.util.Map;
import java.util.stream.Collectors;

public interface StructureChangeStrategy {

	String supports();

	String workLabel();

	OrchidGroupLineageRelationType lineageType();

	default boolean allowsMixedVarieties() {
		return false;
	}

	default boolean requiresEverySourceResult() {
		return true;
	}

	default boolean preservesSourceAttributes() {
		return false;
	}

	default Map<Long, Integer> transformedQuantities(StructureChangeCommand request) {
		return request.sources()
			.stream()
			.collect(Collectors.toMap(source -> source.sourceOrchidGroupId(), source -> source.inputQuantity()));
	}

	default void validate(StructureChangeCommand request) {
		long totalInput = request.sources().stream().mapToLong(source -> source.inputQuantity()).sum();
		long totalResult = request.results().stream().mapToLong(result -> result.quantity()).sum();
		if (totalResult > totalInput) {
			throw new IllegalArgumentException(workLabel() + " 결과 수량은 투입 수량보다 클 수 없습니다.");
		}
	}

}

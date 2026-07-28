package com.greenhouse.backend.farm.dto.variety;

import java.util.List;

public record VarietyGeneraResponse(
		List<String> genera,
		List<VarietyNameResponse> varieties) {
}

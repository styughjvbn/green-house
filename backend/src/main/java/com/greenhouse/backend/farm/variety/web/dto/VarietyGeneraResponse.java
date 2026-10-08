package com.greenhouse.backend.farm.variety.web.dto;

import java.util.List;

public record VarietyGeneraResponse(List<String> genera, List<VarietyNameResponse> varieties) {}

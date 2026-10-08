package com.greenhouse.backend.work.operation.web.dto;

import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import java.util.List;

public record WorkTypeMetadataResponse(List<WorkTypeTemplate> customTypeTemplates) {}

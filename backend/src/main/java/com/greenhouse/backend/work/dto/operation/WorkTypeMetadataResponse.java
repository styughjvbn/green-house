package com.greenhouse.backend.work.dto.operation;

import com.greenhouse.backend.work.api.operation.WorkTypeTemplate;
import java.util.List;

public record WorkTypeMetadataResponse(List<WorkTypeTemplate> customTypeTemplates) {}

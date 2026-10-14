package com.greenhouse.backend.work.operation.web.dto;

import java.time.LocalDate;

public record WorkOperationCompleteRequest(LocalDate completedDate) {}

package com.greenhouse.backend.sales.document.domain;

public record SalesPhotoReference(
    Long salesSlipId, String storageKey, Long byteSize, String sha256) {}

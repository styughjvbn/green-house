package com.greenhouse.backend.sales.api.partner;

import io.swagger.v3.oas.annotations.media.Schema;

/** Current master data for application callers; contains no managed entity. */
@Schema(name = "BusinessPartnerResponse")
@com.fasterxml.jackson.annotation.JsonPropertyOrder({
  "id",
  "name",
  "partnerType",
  "ownerName",
  "phone",
  "address",
  "memo",
  "active"
})
public record BusinessPartnerInfo(
    Long id,
    String name,
    PartnerType partnerType,
    boolean active,
    String ownerName,
    String phone,
    String address,
    String memo) {}

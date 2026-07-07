package com.greenhouse.backend.partner.dto;

import com.greenhouse.backend.partner.domain.PartnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BusinessPartnerCreateRequest(
		@NotBlank @Size(max = 150) String name,
		@NotNull PartnerType partnerType,
		@Size(max = 100) String ownerName,
		@Size(max = 50) String phone,
		@Size(max = 1000) String address,
		@Size(max = 1000) String memo) {
}

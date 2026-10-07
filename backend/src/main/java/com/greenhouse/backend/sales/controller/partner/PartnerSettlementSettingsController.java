package com.greenhouse.backend.sales.controller.partner;

import com.greenhouse.backend.common.api.ApiResponse;
import com.greenhouse.backend.sales.application.partner.PartnerSettlementSettingsService;
import com.greenhouse.backend.sales.dto.partner.PartnerSettlementSettingsRequest;
import com.greenhouse.backend.sales.dto.partner.PartnerSettlementSettingsResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business-partners/{partnerId}/settlement-settings")
@RequiredArgsConstructor
public class PartnerSettlementSettingsController {

  private final PartnerSettlementSettingsService settingsService;

  @GetMapping
  public ApiResponse<PartnerSettlementSettingsResponse> get(@PathVariable Long partnerId) {
    return ApiResponse.ok(settingsService.getOrCreate(partnerId));
  }

  @PutMapping
  public ApiResponse<PartnerSettlementSettingsResponse> update(
      @PathVariable Long partnerId, @Valid @RequestBody PartnerSettlementSettingsRequest request) {
    return ApiResponse.ok(settingsService.update(partnerId, request));
  }
}

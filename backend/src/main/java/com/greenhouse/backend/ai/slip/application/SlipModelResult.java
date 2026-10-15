package com.greenhouse.backend.ai.slip.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SlipModelResult(
    @Pattern(regexp = "DIRECT|AUCTION") String salesType,
    @Size(max = 10) String saleDate,
    @Size(max = 200) String partnerName,
    @Positive Long partnerId,
    @Size(max = 1000) String memo,
    @NotNull @Size(max = 100) List<@Valid Item> items,
    @NotNull @Size(max = 30) List<@NotNull @Size(max = 500) String> warnings,
    @NotNull @Min(0) @Max(100) Integer confidence,
    @Min(1) @Max(2048) Integer imageLongEdge,
    @Min(1) @Max(2) Integer attempts) {
  public record Item(
      @Size(max = 150) String itemName,
      @Size(max = 100) String genus,
      @Positive Long varietyId,
      @Size(max = 100) String spec,
      @Size(max = 30) String unit,
      @Min(1) Integer boxes,
      @Min(1) Integer quantity,
      @Min(0) Integer unitPrice,
      @Min(0) Long amount,
      @Size(max = 1000) String memo) {}
}

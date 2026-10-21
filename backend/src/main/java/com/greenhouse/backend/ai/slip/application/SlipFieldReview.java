package com.greenhouse.backend.ai.slip.application;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(name = "AiSlipFieldReview")
public record SlipFieldReview(
    @NotNull Field field,
    @Schema(nullable = true, description = "품목은 0부터 시작하는 행 번호, 전표 기본 정보는 null") @Min(0) @Max(99)
        Integer itemIndex,
    @NotBlank @Size(max = 200) String reason) {
  public enum Field {
    SALE_DATE(false),
    PARTNER(false),
    SALES_TYPE(false),
    SLIP_MEMO(false),
    ITEM_NAME(true),
    GENUS(true),
    GRADE(true),
    UNIT(true),
    BOXES(true),
    QUANTITY(true),
    UNIT_PRICE(true),
    AMOUNT(true),
    ITEM_MEMO(true);

    private final boolean itemField;

    Field(boolean itemField) {
      this.itemField = itemField;
    }

    public boolean itemField() {
      return itemField;
    }
  }
}

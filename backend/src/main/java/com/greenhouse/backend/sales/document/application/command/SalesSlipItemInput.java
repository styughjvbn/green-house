package com.greenhouse.backend.sales.document.application.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(name = "SalesSlipItemRequest")
public record SalesSlipItemInput(
    @NotBlank @Size(max = 150) String itemName,
    @Size(max = 100) String genus,
    @Size(max = 100) String spec,
    @NotNull @Min(1) Integer quantity,
    @NotNull @Min(0) Integer unitPrice,
    @Size(max = 1000) String memo,
    @NotNull List<@Valid SalesSlipAllocationInput> allocations,
    @JsonInclude(JsonInclude.Include.NON_NULL)
        @Min(1)
        @JsonDeserialize(using = UnitDeserializer.class)
        Integer unit,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Min(1) Integer boxes) {
  public SalesSlipItemInput(
      String itemName,
      String genus,
      String spec,
      Integer quantity,
      Integer unitPrice,
      String memo,
      List<SalesSlipAllocationInput> allocations) {
    this(itemName, genus, spec, quantity, unitPrice, memo, allocations, null, null);
  }

  /** Reject fractional values instead of Jackson's default integer truncation. */
  public static class UnitDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
        return (Integer) context.handleUnexpectedToken(Integer.class, parser);
      return parser.getIntValue();
    }
  }
}

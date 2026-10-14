package com.greenhouse.backend.sales.document.application.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

@Schema(
    name = "SalesInitialReceiptRequest",
    description = "실제 받은 전액을 기록합니다. 전표 총액까지 배분하고 초과분은 거래처 미배분 잔액으로 남깁니다.")
public record SalesInitialReceiptInput(
    @NotNull @Positive Long amount, @NotNull LocalDate paymentDate, @NotNull Method method) {
  public enum Method {
    CASH,
    BANK_TRANSFER
  }
}

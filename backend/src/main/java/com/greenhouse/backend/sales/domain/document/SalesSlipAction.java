package com.greenhouse.backend.sales.domain.document;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SalesSlipAction")
public enum SalesSlipAction {
  EDIT,
  COMPLETE,
  CANCEL,
  CONFIRM_PAYMENT
}

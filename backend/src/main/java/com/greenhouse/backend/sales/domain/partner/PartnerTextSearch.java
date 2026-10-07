package com.greenhouse.backend.sales.domain.partner;

import java.util.Objects;

public record PartnerTextSearch(PartnerTextMatch match, String value) {
  public PartnerTextSearch {
    Objects.requireNonNull(match);
    Objects.requireNonNull(value);
  }
}

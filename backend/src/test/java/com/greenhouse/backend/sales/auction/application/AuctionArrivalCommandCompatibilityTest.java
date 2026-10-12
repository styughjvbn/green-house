package com.greenhouse.backend.sales.auction.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationDetails;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AuctionArrivalCommandCompatibilityTest {
  @Test
  void oldArrivalFingerprintInputKeepsItsOriginalProperties() throws Exception {
    var mapper =
        JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    var details =
        new OrchidGroupMutationDetails(
            9L, 5, "3.5치", null, "정상", "POT", null, false, null, null, null);
    var command =
        new AuctionArrivalCommand(LocalDate.of(2026, 10, 9), 1L, details, "old-key", null);
    var input = mapper.valueToTree(command);
    assertThat(input.size()).isEqualTo(5);
    assertThat(input.has("quantity")).isFalse();
    assertThat(input.has("recordOnly")).isFalse();
    assertThat(input.has("worker")).isTrue();
    assertThat(mapper.readValue(mapper.writeValueAsBytes(command), AuctionArrivalCommand.class))
        .isEqualTo(command);
    assertThat(command.arrivalQuantity()).isEqualTo(5);
  }
}

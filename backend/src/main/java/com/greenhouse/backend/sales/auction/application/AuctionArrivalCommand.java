package com.greenhouse.backend.sales.auction.application;

import com.greenhouse.backend.farm.api.orchid.mutation.OrchidGroupMutationDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AuctionArrivalCommand(
    @NotNull LocalDate arrivalDate,
    @NotNull @Positive Long bedZoneId,
    @NotNull @Valid OrchidGroupMutationDetails details,
    @NotBlank @Size(max = 100) String idempotencyKey,
    @Size(max = 100) String worker) {}

package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import java.math.BigDecimal;

public record OrchidGroupMutationGraphLocationResponse(
    Integer houseNumber,
    Integer physicalBedNumber,
    BedZoneSide side,
    String bedZoneName,
    BigDecimal startPosition,
    BigDecimal endPosition) {}

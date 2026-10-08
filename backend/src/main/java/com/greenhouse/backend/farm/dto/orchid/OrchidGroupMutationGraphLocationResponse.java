package com.greenhouse.backend.farm.dto.orchid;

import com.greenhouse.backend.farm.structure.domain.BedZoneSide;
import java.math.BigDecimal;

public record OrchidGroupMutationGraphLocationResponse(
    Integer houseNumber,
    Integer physicalBedNumber,
    BedZoneSide side,
    String bedZoneName,
    BigDecimal startPosition,
    BigDecimal endPosition) {}

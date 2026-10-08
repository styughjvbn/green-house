package com.greenhouse.backend.farm.structure.repository;

import com.greenhouse.backend.farm.structure.domain.BedZoneSide;

public record BedZoneLocationRow(
    Long id,
    Integer houseNumber,
    Integer physicalBedNumber,
    BedZoneSide side,
    String bedZoneName) {}

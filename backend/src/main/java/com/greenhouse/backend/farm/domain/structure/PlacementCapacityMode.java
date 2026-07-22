package com.greenhouse.backend.farm.domain.structure;

public enum PlacementCapacityMode {
	SPACIOUS,
	STANDARD,
	EXPANDED,
	COMPRESSED,
	TEMPORARY;

	public boolean atLeast(PlacementCapacityMode other) {
		return ordinal() >= other.ordinal();
	}
}

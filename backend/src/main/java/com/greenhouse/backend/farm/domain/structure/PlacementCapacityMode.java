package com.greenhouse.backend.farm.domain.structure;

public enum PlacementCapacityMode {
	SPACIOUS(10),
	STANDARD(20),
	EXPANDED(30),
	COMPRESSED(40),
	TEMPORARY(50);

	private final int strength;

	PlacementCapacityMode(int strength) {
		this.strength = strength;
	}

	public int strength() {
		return strength;
	}

	public boolean atLeast(PlacementCapacityMode other) {
		return strength >= other.strength;
	}
}

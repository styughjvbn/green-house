package com.greenhouse.backend.work.domain.operation;

public enum WorkTypeTemplate {
	PESTICIDE,
	FERTILIZER,
	REPOT,
	CLEANUP,
	DISCARD,
	STATUS,
	MEMO,
	MOVEMENT,
	MULTI_CREATE,
	CORRECTION;

	public boolean isCustomTypeAllowed() {
		return switch (this) {
			case PESTICIDE, FERTILIZER, CLEANUP, STATUS, MEMO -> true;
			case REPOT, DISCARD, MOVEMENT, MULTI_CREATE, CORRECTION -> false;
		};
	}
}

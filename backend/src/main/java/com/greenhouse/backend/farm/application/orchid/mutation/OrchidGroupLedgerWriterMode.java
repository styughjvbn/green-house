package com.greenhouse.backend.farm.application.orchid.mutation;

/**
 * ORCHID-CUTOVER: TRANSITION_ONLY — 운영 authority 전환 전까지만 선택 가능한 writer mode다. Removal
 * gate: 모든 환경의 Engine 단일 writer 고정.
 */
public enum OrchidGroupLedgerWriterMode {

	LEGACY, ENGINE

}

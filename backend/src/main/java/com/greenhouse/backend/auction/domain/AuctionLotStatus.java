package com.greenhouse.backend.auction.domain;

// TODO: lot 현재 상태, 경매 시도 결과, 검수/정합성 플래그가 섞여 있다.
// SHIPPED/FAILED/IN_PROGRESS는 실제 전이 기준으로 정리하고, attempt/inspection 상태와 분리한다.
public enum AuctionLotStatus {
	SHIPPED,
	WAITING,
	IN_PROGRESS,
	SOLD,
	PARTIALLY_SOLD,
	FAILED,
	REAUCTION_WAITING,
	RETURN_INFERRED,
	PARTIALLY_RETURNED,
	RETURNED,
	QUANTITY_MISMATCH,
	REVIEW_REQUIRED,
	CANCELLED
}

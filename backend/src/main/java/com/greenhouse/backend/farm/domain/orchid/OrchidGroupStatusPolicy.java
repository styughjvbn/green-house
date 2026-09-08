package com.greenhouse.backend.farm.domain.orchid;

import java.util.List;

public final class OrchidGroupStatusPolicy {

	public static final String WARNING = "주의";

	public static final String ABNORMAL = "이상";

	public static final String PEST_DISEASE = "병해충";

	public static final String CLOSED = "종료";

	public static final String DISCARDED = "폐기";

	public static final String SOLD_OUT = "판매 완료";

	public static final String CREATION_CANCELED = "생성 취소";

	private static final List<String> WARNING_STATUSES = List.of(WARNING, ABNORMAL, PEST_DISEASE);

	private static final List<String> INACTIVE_STATUSES = List.of(CLOSED, DISCARDED, SOLD_OUT, CREATION_CANCELED);

	private static final List<String> UNAVAILABLE_FOR_SALE_STATUSES = List.of(WARNING, ABNORMAL, PEST_DISEASE, CLOSED,
			DISCARDED, SOLD_OUT, CREATION_CANCELED);

	private OrchidGroupStatusPolicy() {
	}

	public static List<String> warningStatuses() {
		return WARNING_STATUSES;
	}

	public static List<String> inactiveStatuses() {
		return INACTIVE_STATUSES;
	}

	public static List<String> unavailableForSaleStatuses() {
		return UNAVAILABLE_FOR_SALE_STATUSES;
	}

	public static boolean isWarning(String status) {
		return WARNING_STATUSES.contains(status);
	}

	public static boolean isInactive(String status) {
		return INACTIVE_STATUSES.contains(status);
	}

	public static boolean isSaleable(String status) {
		return !UNAVAILABLE_FOR_SALE_STATUSES.contains(status);
	}

}

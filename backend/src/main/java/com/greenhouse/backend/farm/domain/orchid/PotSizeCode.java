package com.greenhouse.backend.farm.domain.orchid;

import java.util.Locale;
import java.util.Map;

public enum PotSizeCode {
	UNSPECIFIED(null),
	UNMAPPED(null),
	POT_2("2\""),
	POT_2_5("2.5\""),
	POT_3("3\""),
	POT_3_5("3.5\""),
	POT_4("4\""),
	POT_4_5("4.5\""),
	POT_5("5\""),
	POT_6("6\""),
	HANGING("행잉"),
	ETC("기타");

	private static final Map<String, PotSizeCode> INPUT_CODES = Map.ofEntries(
			Map.entry("2", POT_2), Map.entry("2\"", POT_2), Map.entry("2치", POT_2), Map.entry("2인치", POT_2),
			Map.entry("2.5", POT_2_5), Map.entry("2.5\"", POT_2_5), Map.entry("2.5치", POT_2_5), Map.entry("2.5인치", POT_2_5),
			Map.entry("3", POT_3), Map.entry("3\"", POT_3), Map.entry("3치", POT_3), Map.entry("3인치", POT_3),
			Map.entry("3.5", POT_3_5), Map.entry("3.5\"", POT_3_5), Map.entry("3.5치", POT_3_5), Map.entry("3.5인치", POT_3_5),
			Map.entry("4", POT_4), Map.entry("4\"", POT_4), Map.entry("4치", POT_4), Map.entry("4인치", POT_4),
			Map.entry("4.5", POT_4_5), Map.entry("4.5\"", POT_4_5), Map.entry("4.5치", POT_4_5), Map.entry("4.5인치", POT_4_5),
			Map.entry("5", POT_5), Map.entry("5\"", POT_5), Map.entry("5치", POT_5), Map.entry("5인치", POT_5),
			Map.entry("6", POT_6), Map.entry("6\"", POT_6), Map.entry("6치", POT_6), Map.entry("6인치", POT_6),
			Map.entry("행잉", HANGING), Map.entry("hanging", HANGING),
			Map.entry("기타", ETC), Map.entry("etc", ETC));

	private final String displayValue;

	PotSizeCode(String displayValue) {
		this.displayValue = displayValue;
	}

	public String getDisplayValue() {
		return displayValue;
	}

	public static PotSizeCode fromInput(String value) {
		if (value == null || value.isBlank()) {
			return UNSPECIFIED;
		}
		PotSizeCode code = INPUT_CODES.get(value.trim().toLowerCase(Locale.ROOT));
		if (code == null) {
			throw new IllegalArgumentException("지원하지 않는 화분 크기입니다. 표준 규격을 선택해주세요.");
		}
		return code;
	}
}

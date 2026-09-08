package com.greenhouse.backend.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DemoRequestPolicyTest {

	@ParameterizedTest
	@CsvSource({ "POST, /api/auth/login, true", "POST, /api/auth/logout, true", "GET, /api/auth/me, false",
			"PUT, /api/business-partners/42/settlement-settings, true",
			"PUT, /api/business-partners/42/settlement-settings/, true",
			"GET, /api/business-partners/42/settlement-settings, false", "PATCH, /api/business-partners/42, false",
			"POST, /api/work-types, true", "PATCH, /api/work-types/12, true", "DELETE, /api/work-types/12, true",
			"GET, /api/work-types, false", "POST, /api/work-operations, false" })
	void protectsSettingsWhileAllowingOrdinaryWork(String method, String path, boolean blocked) {
		assertThat(DemoRequestPolicy.blocks(method, path)).isEqualTo(blocked);
	}

}

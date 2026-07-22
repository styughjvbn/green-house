package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.domain.orchid.PotSizeCode;
import org.junit.jupiter.api.Test;

class PotSizeCodeTests {

	@Test
	void mapsKnownAliasesToStandardCodes() {
		assertThat(PotSizeCode.fromInput("3.5치")).isEqualTo(PotSizeCode.POT_3_5);
		assertThat(PotSizeCode.fromInput("4.5치")).isEqualTo(PotSizeCode.POT_4_5);
		assertThat(PotSizeCode.fromInput("6\"")).isEqualTo(PotSizeCode.POT_6);
		assertThat(PotSizeCode.fromInput(null)).isEqualTo(PotSizeCode.UNSPECIFIED);
	}

	@Test
	void rejectsUnknownValuesInsteadOfMergingThem() {
		assertThatThrownBy(() -> PotSizeCode.fromInput("23\""))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("표준 규격");
	}
}

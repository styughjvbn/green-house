package com.greenhouse.backend.farm.domain.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.domain.structure.BedPlacementProfilePolicy.CapacityRule;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BedPlacementProfilePolicyTest {

	@Test
	void normalizesValuesAndPreservesInputOrder() {
		var rules = List.of(new CapacityRule(" tray_20 ", " 3인치 ", PlacementCapacityMode.EXPANDED,
				new BigDecimal("6.125"), 5, true, " 메모 "), rule("TRAY_20", "3인치", PlacementCapacityMode.STANDARD, 4));

		var capacities = BedPlacementProfilePolicy.createCapacities(rules);

		assertThat(capacities).extracting(BedZoneCapacity::getCapacityMode)
			.containsExactly(PlacementCapacityMode.EXPANDED, PlacementCapacityMode.STANDARD);
		assertThat(capacities.getFirst().getPlacementType()).isEqualTo("TRAY_20");
		assertThat(capacities.getFirst().getPotSize()).isEqualTo("3\"");
		assertThat(capacities.getFirst().getUnitSpan()).isEqualByComparingTo("6.13");
		assertThat(capacities.getFirst().getMemo()).isEqualTo("메모");
	}

	@ParameterizedTest
	@ValueSource(
			strings = { "TRAY_12", "TRAY_15", "TRAY_20", "TRAY_24", "SINGLE_POT", "HANGING", "CUSTOM:NEW", "CUSTOM:" })
	void supportsExistingTypesAndCustomRules(String type) {
		var capacity = BedPlacementProfilePolicy
			.createCapacities(List.of(rule(type, null, PlacementCapacityMode.STANDARD, 1)))
			.getFirst();
		assertThat(capacity.getPlacementType()).isEqualTo(type);
		assertThat(capacity.getPotSize()).isNull();
	}

	@Test
	void rejectsDuplicateRulesAfterTrimmingAndCaseNormalization() {
		assertThatThrownBy(() -> BedPlacementProfilePolicy
			.createCapacities(List.of(rule("tray_20 ", " ", PlacementCapacityMode.STANDARD, 4),
					rule(" TRAY_20", null, PlacementCapacityMode.STANDARD, 4))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("같은 수용 규칙이 중복되었습니다.");
	}

	@Test
	void comparesCapacityInStrengthOrderRegardlessOfRequestOrder() {
		assertThatThrownBy(() -> BedPlacementProfilePolicy
			.createCapacities(List.of(rule("TRAY_20", null, PlacementCapacityMode.TEMPORARY, 4),
					rule("TRAY_20", null, PlacementCapacityMode.SPACIOUS, 5))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("강한 배치 모드의 수용량은 이전 모드보다 작을 수 없습니다.");
		assertThat(PlacementCapacityMode.TEMPORARY.atLeast(PlacementCapacityMode.COMPRESSED)).isTrue();
		assertThat(PlacementCapacityMode.STANDARD.atLeast(PlacementCapacityMode.STANDARD)).isTrue();
		assertThat(PlacementCapacityMode.SPACIOUS.atLeast(PlacementCapacityMode.STANDARD)).isFalse();
	}

	@Test
	void keepsTypesAndPotSizesIndependentAndAllowsEqualCapacities() {
		var rules = List.of(rule("TRAY_20", "3", PlacementCapacityMode.STANDARD, 10),
				rule("TRAY_20", "3", PlacementCapacityMode.COMPRESSED, 10),
				rule("TRAY_20", "4", PlacementCapacityMode.COMPRESSED, 2),
				rule("CUSTOM:NEW", "3", PlacementCapacityMode.COMPRESSED, 1));
		assertThat(BedPlacementProfilePolicy.createCapacities(rules)).hasSize(4);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1" })
	void rejectsNonPositiveUnitSpans(String span) {
		var rule = new CapacityRule("TRAY_20", null, PlacementCapacityMode.STANDARD, new BigDecimal(span), 1, true,
				null);
		assertThatThrownBy(() -> BedPlacementProfilePolicy.createCapacities(List.of(rule)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("점유 폭은 0보다 커야 합니다.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "UNSUPPORTED", "CUSTOM", "" })
	void rejectsUnsupportedAndEmptyPlacementTypes(String type) {
		assertThatThrownBy(() -> BedPlacementProfilePolicy
			.createCapacities(List.of(rule(type, null, PlacementCapacityMode.STANDARD, 1))))
			.isInstanceOf(IllegalArgumentException.class);
	}

	private CapacityRule rule(String type, String potSize, PlacementCapacityMode mode, int capacity) {
		return new CapacityRule(type, potSize, mode, BigDecimal.valueOf(6), capacity, true, null);
	}

}

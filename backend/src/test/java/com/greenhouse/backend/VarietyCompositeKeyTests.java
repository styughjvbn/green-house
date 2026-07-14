package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.VarietyService;
import com.greenhouse.backend.farm.dto.VarietyCreateRequest;
import com.greenhouse.backend.farm.dto.VarietyUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VarietyCompositeKeyTests {

	@Autowired VarietyService varietyService;

	@Test
	void allowsSameNameWhenGenusIsDifferent() {
		varietyService.create(createRequest("카틀레야", "골드"));

		var created = varietyService.create(createRequest("심비디움", "골드"));

		assertThat(created.genus()).isEqualTo("심비디움");
		assertThat(created.name()).isEqualTo("골드");
	}

	@Test
	void rejectsDuplicateGenusAndNameOnCreate() {
		varietyService.create(createRequest("카틀레야", "골드"));

		assertThatThrownBy(() -> varietyService.create(createRequest("카틀레야", "골드")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("같은 속과 품종명");
	}

	@Test
	void rejectsDuplicateGenusAndNameOnUpdate() {
		varietyService.create(createRequest("카틀레야", "골드"));
		var target = varietyService.create(createRequest("심비디움", "골드"));

		assertThatThrownBy(() -> varietyService.update(target.id(), updateRequest("카틀레야", "골드")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("같은 속과 품종명");
	}

	private VarietyCreateRequest createRequest(String genus, String name) {
		return new VarietyCreateRequest(genus, name, null, null, true, null, null);
	}

	private VarietyUpdateRequest updateRequest(String genus, String name) {
		return new VarietyUpdateRequest(genus, name, null, null, true, null, null);
	}
}

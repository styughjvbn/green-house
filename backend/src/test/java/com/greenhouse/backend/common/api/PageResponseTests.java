package com.greenhouse.backend.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTests {

	@Test
	void copiesContentAndPaginationMetadata() {
		var page = new PageImpl<>(List.of("첫째", "둘째"), PageRequest.of(1, 2), 5);

		var response = PageResponse.from(page);

		assertThat(response.content()).containsExactly("첫째", "둘째");
		assertThat(response.page()).isEqualTo(1);
		assertThat(response.size()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(5);
		assertThat(response.totalPages()).isEqualTo(3);
	}
}

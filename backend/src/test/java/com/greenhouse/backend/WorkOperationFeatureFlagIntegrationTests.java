package com.greenhouse.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.work.domain.WorkType;
import com.greenhouse.backend.work.domain.WorkTypeTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

@SpringBootTest(properties = "app.features.work-operation-v2-enabled=false")
class WorkOperationFeatureFlagIntegrationTests extends AbstractBackendIntegrationTest {

	private WorkType pesticideType;

	@BeforeEach
	void setUp() {
		workRecordRepository.deleteAll();
		workTypeRepository.deleteAll();
		pesticideType = workTypeRepository.save(new WorkType(
				"PESTICIDE", "농약", WorkTypeTemplate.PESTICIDE, true, false, true, 1));
	}

	@Test
	void restoresLegacyManualWritesWhenWorkOperationsAreDisabled() throws Exception {
		mockMvc.perform(get("/api/work-operations"))
				.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/work-records")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "workTypeId": %d,
						  "workDate": "2026-07-15",
						  "targetType": "FARM",
						  "memo": "기능 플래그 복귀 검증"
						}
						""".formatted(pesticideType.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.workType").value("농약"));
	}
}

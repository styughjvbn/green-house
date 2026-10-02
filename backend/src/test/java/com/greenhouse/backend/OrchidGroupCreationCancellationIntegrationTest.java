package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.sales.domain.SalesSlip;
import com.greenhouse.backend.sales.domain.SalesSlipItem;
import com.greenhouse.backend.sales.domain.SalesSlipItemAllocation;
import com.greenhouse.backend.sales.domain.SalesType;
import com.greenhouse.backend.work.domain.effect.WorkAppliedEffect;
import com.greenhouse.backend.work.domain.effect.WorkEffectKind;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroup;
import com.greenhouse.backend.work.domain.effect.WorkEffectOrchidGroupRelationType;
import com.greenhouse.backend.work.domain.operation.WorkOperation;
import com.greenhouse.backend.work.domain.operation.WorkOperationStatus;
import com.greenhouse.backend.work.domain.operation.WorkSourceScopeType;
import com.greenhouse.backend.work.domain.operation.WorkType;
import com.greenhouse.backend.work.domain.target.WorkOperationTarget;
import com.greenhouse.backend.work.domain.target.WorkTargetInclusionSource;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrchidGroupCreationCancellationIntegrationTest extends FarmFixtureIntegrationTest {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 5);

	@Test
	void allowsCanceledAndVoidedReferencesAndPreservesTheirHistory() throws Exception {
		var group = group();
		var canceled = operation(group, true);
		canceled.cancel(DATE.atStartOfDay());
		var voided = operation(group, true);
		voided.complete(DATE.atStartOfDay());
		voided.voidCompletedMutationWork(DATE.atTime(1, 0), "오등록", "cancel", 100L);
		fixtureEntityManager.flush();
		long linksBefore = countLinks(group.getId());

		mockMvc.perform(delete("/api/orchid-groups/{id}", group.getId())).andExpect(status().isOk());
		fixtureEntityManager.flush();
		fixtureEntityManager.clear();
		var persisted = orchidGroupRepository.findById(group.getId()).orElseThrow();
		assertThat(persisted.getQuantity()).isZero();
		assertThat(persisted.getStatus()).isEqualTo("생성 취소");
		assertThat(countLinks(group.getId())).isEqualTo(linksBefore);
		assertThat(fixtureEntityManager.find(WorkOperation.class, canceled.getId()).getStatus())
			.isEqualTo(WorkOperationStatus.CANCELED);
		assertThat(fixtureEntityManager.find(WorkOperation.class, voided.getId()).getStatus())
			.isEqualTo(WorkOperationStatus.VOIDED);
	}

	@ParameterizedTest
	@EnumSource(value = WorkOperationStatus.class, names = { "PLANNED", "COMPLETED", "STOPPED" })
	void blocksUncanceledWorkEvenAlongsideCanceledHistory(WorkOperationStatus state) throws Exception {
		var group = group();
		operation(group, true).cancel(DATE.atStartOfDay());
		var active = operation(group, state != WorkOperationStatus.PLANNED);
		if (state == WorkOperationStatus.COMPLETED)
			active.complete(DATE.atStartOfDay());
		if (state == WorkOperationStatus.STOPPED)
			active.stop(DATE.atStartOfDay());
		fixtureEntityManager.flush();

		mockMvc.perform(delete("/api/orchid-groups/{id}", group.getId())).andExpect(status().isConflict());
		assertThat(group.getQuantity()).isEqualTo(20);
		assertThat(group.getStatus()).isEqualTo("정상");
	}

	@Test
	void blocksActiveResultReferenceWithoutATarget() throws Exception {
		var group = group();
		var type = type();
		var operation = new WorkOperation(type, "결과 작업", DATE, DATE, WorkSourceScopeType.MANUAL_SELECTION, null,
				Map.of(), Map.of(), "worker", null, DATE.atStartOfDay());
		fixtureEntityManager.persist(operation);
		effect(operation, group);
		fixtureEntityManager.flush();
		mockMvc.perform(delete("/api/orchid-groups/{id}", group.getId())).andExpect(status().isConflict());
	}

	@Test
	void salesReferenceStillBlocksCreationCancellationWithCanceledWork() throws Exception {
		var group = group();
		operation(group, true).cancel(DATE.atStartOfDay());
		var partner = new BusinessPartner("생성 취소 검증", PartnerType.WHOLESALE, null, null, null, null);
		fixtureEntityManager.persist(partner);
		var slip = new SalesSlip("CANCEL-SALES", DATE, SalesType.DIRECT, null, partner.getId(), "미입금",
				SalesSlip.STATUS_DRAFT, null, null);
		var item = new SalesSlipItem(null, group.getVarietyName(), null, null, 2, 1000, null);
		item.addAllocation(new SalesSlipItemAllocation(group.getId(), 2));
		slip.addItem(item);
		fixtureEntityManager.persist(slip);
		fixtureEntityManager.flush();
		mockMvc.perform(delete("/api/orchid-groups/{id}", group.getId())).andExpect(status().isConflict());
		assertThat(group.getStatus()).isEqualTo("정상");
	}

	@Test
	void reservationStillBlocksCreationCancellation() throws Exception {
		var group = group();
		operation(group, true).cancel(DATE.atStartOfDay());
		group.reserve(1);
		fixtureEntityManager.flush();
		mockMvc.perform(delete("/api/orchid-groups/{id}", group.getId())).andExpect(status().isBadRequest());
		assertThat(group.getStatus()).isEqualTo("정상");
	}

	private OrchidGroup group() {
		var fixtures = new FarmTestFixtures(fixtureEntityManager);
		return fixtures.orchidGroup(fixtures.layout(987).left(), "CANCEL-ONLY", 20);
	}

	private WorkType type() {
		return workTypeRepository.findByCode("MOVEMENT").orElseThrow();
	}

	private WorkOperation operation(OrchidGroup group, boolean withEffect) {
		var operation = new WorkOperation(type(), "참조 작업", DATE, DATE, WorkSourceScopeType.ORCHID_GROUP, group.getId(),
				Map.of(), Map.of(), "worker", null, DATE.atStartOfDay());
		fixtureEntityManager.persist(operation);
		fixtureEntityManager.persist(new WorkOperationTarget(operation, group.getId(), WorkTargetInclusionSource.DIRECT,
				group.getId(), group.getVariety().getId(), group.getVarietyName(), group.getAgeYear(),
				group.getPotSizeCode().name(), group.getPotSize(), group.getQuantity(), Map.of(), DATE.atStartOfDay()));
		if (withEffect)
			effect(operation, group);
		return operation;
	}

	private void effect(WorkOperation operation, OrchidGroup group) {
		var effect = new WorkAppliedEffect(operation, null, "effect", WorkEffectKind.RECORD_ONLY, "RECORD_ONLY",
				DATE.atStartOfDay(), "worker", Map.of(), Map.of());
		fixtureEntityManager.persist(effect);
		fixtureEntityManager
			.persist(new WorkEffectOrchidGroup(effect, group.getId(), WorkEffectOrchidGroupRelationType.RESULT));
	}

	private long countLinks(Long groupId) {
		return fixtureEntityManager
			.createQuery("select count(link) from WorkEffectOrchidGroup link where link.orchidGroupId = :id",
					Long.class)
			.setParameter("id", groupId)
			.getSingleResult();
	}

}

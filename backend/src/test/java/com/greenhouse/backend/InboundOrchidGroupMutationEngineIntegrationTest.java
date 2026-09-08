package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.farm.application.orchid.mutation.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.application.orchid.mutation.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationEntryRole;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSource;
import com.greenhouse.backend.farm.domain.orchid.mutation.OrchidGroupMutationSourceDomain;
import com.greenhouse.backend.farm.domain.structure.BedZone;
import com.greenhouse.backend.farm.domain.structure.BedZoneSide;
import com.greenhouse.backend.farm.domain.structure.House;
import com.greenhouse.backend.farm.domain.structure.PhysicalBed;
import com.greenhouse.backend.farm.domain.variety.Variety;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationEntryRepository;
import com.greenhouse.backend.farm.repository.orchid.mutation.OrchidGroupMutationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class InboundOrchidGroupMutationEngineIntegrationTest extends AbstractBackendIntegrationTest {

	@Autowired
	private OrchidGroupMutationEngine mutationEngine;

	@Autowired
	private OrchidGroupMutationRepository mutationRepository;

	@Autowired
	private OrchidGroupMutationEntryRepository entryRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void createsMultipleInboundGroupsWithProvenanceAndAutoPlacementExactlyOnce() {
		Fixture fixture = createFixture();
		long groupCountBefore = orchidGroupRepository.count();
		long mutationCountBefore = mutationRepository.count();
		long entryCountBefore = entryRepository.count();
		LocalDate businessDate = LocalDate.of(2026, 8, 20);
		OrchidGroupMutationSource source = new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.WORK,
				"INBOUND_POTTING_EFFECT", "7101", "POTTING:round-1", UUID.randomUUID());
		var command = new CreateInboundOrchidGroupsMutationCommand(source, fixture.inboundRecord().getId(),
				List.of(new CreateOrchidGroupMutationItem(fixture.bedZone().getId(),
						details(fixture.variety().getId(), 60, "2치")),
						new CreateOrchidGroupMutationItem(fixture.bedZone().getId(),
								details(fixture.variety().getId(), 40, "2\""))),
				businessDate, "입고 포트 작업");

		var created = mutationEngine.createFromInbound(command);
		entityManager.flush();
		entityManager.clear();
		var replayed = mutationEngine.createFromInbound(new CreateInboundOrchidGroupsMutationCommand(source,
				fixture.inboundRecord().getId(), command.groups(), businessDate, " 입고 포트 작업 "));

		assertThat(replayed.mutationId()).isEqualTo(created.mutationId());
		assertThat(created.entries()).hasSize(2).allSatisfy(entry -> {
			assertThat(entry.role()).isEqualTo(OrchidGroupMutationEntryRole.RESULT);
			assertThat(entry.stateRevisionAfter()).isEqualTo(1L);
			assertThat(entry.afterState().inboundRecordId()).isEqualTo(fixture.inboundRecord().getId());
		});
		assertThat(created.entries()).extracting(entry -> entry.afterState().startPosition())
			.containsExactly(new BigDecimal("0.00"), new BigDecimal("1.00"));
		assertThat(created.entries()).extracting(entry -> entry.afterState().endPosition())
			.containsExactly(new BigDecimal("1.00"), new BigDecimal("2.00"));
		assertThat(orchidGroupRepository.count()).isEqualTo(groupCountBefore + 2);
		assertThat(mutationRepository.count()).isEqualTo(mutationCountBefore + 1);
		assertThat(entryRepository.count()).isEqualTo(entryCountBefore + 2);

		assertThatThrownBy(() -> mutationEngine.createFromInbound(new CreateInboundOrchidGroupsMutationCommand(
				new OrchidGroupMutationSource(OrchidGroupMutationSourceDomain.INBOUND, "INBOUND_RECORD",
						fixture.inboundRecord().getId().toString(), "CREATE_PLACED_GROUP", UUID.randomUUID()),
				fixture.inboundRecord().getId(),
				List.of(new CreateOrchidGroupMutationItem(fixture.bedZone().getId(),
						details(fixture.variety().getId(), 10, "2치"))),
				businessDate, "중복 입고 생성")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("이미 난 묶음이 생성");
	}

	private Fixture createFixture() {
		House house = new House(906, "Inbound Mutation 테스트동");
		PhysicalBed bed = new PhysicalBed(1, 1);
		bed.updatePositionUnits(new BigDecimal("20"), "칸");
		BedZone bedZone = new BedZone("입고 구역", BedZoneSide.LEFT, 1);
		bed.addBedZone(bedZone);
		house.addPhysicalBed(bed);
		houseRepository.save(house);
		Variety variety = varietyRepository.save(new Variety("INBOUND-MUTATION", "Phalaenopsis", "Inbound Mutation",
				null, "2치", true, true, null, null));
		InboundRecord inboundRecord = inboundRecordRepository.save(new InboundRecord(LocalDate.of(2026, 8, 19),
				InboundType.FLASK_SEEDLING, variety, InboundStatus.POTTING_PENDING, 10, 100, null, "배양실",
				LocalDate.of(2026, 8, 20), "2치", 1, null, null, null, null, "입고 담당", null));
		return new Fixture(bedZone, variety, inboundRecord);
	}

	private OrchidGroupMutationDetails details(Long varietyId, int quantity, String potSize) {
		return new OrchidGroupMutationDetails(varietyId, quantity, potSize, 1, "정상", "TRAY", 1, false, null, null,
				null);
	}

	private record Fixture(BedZone bedZone, Variety variety, InboundRecord inboundRecord) {
	}

}

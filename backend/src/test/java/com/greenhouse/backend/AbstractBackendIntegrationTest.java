package com.greenhouse.backend;

import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.material.MaterialRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.structure.BedZoneRepository;
import com.greenhouse.backend.farm.repository.structure.HouseRepository;
import com.greenhouse.backend.farm.repository.structure.PhysicalBedRepository;
import com.greenhouse.backend.farm.repository.variety.VarietyRepository;
import com.greenhouse.backend.farm.support.FarmTestFixtures;
import com.greenhouse.backend.work.repository.WorkCommandReceiptRepository;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AbstractBackendIntegrationTest {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected HouseRepository houseRepository;

	@Autowired
	protected PhysicalBedRepository physicalBedRepository;

	@Autowired
	protected BedZoneRepository bedZoneRepository;

	@Autowired
	protected OrchidGroupRepository orchidGroupRepository;

	@Autowired
	protected WorkCommandReceiptRepository workCommandReceiptRepository;

	@Autowired
	protected WorkTypeRepository workTypeRepository;

	@Autowired
	protected VarietyRepository varietyRepository;

	@Autowired
	protected InboundRecordRepository inboundRecordRepository;

	@Autowired
	protected MaterialRepository materialRepository;

	@Autowired
	private EntityManager baselineEntityManager;

	@Autowired
	private PlatformTransactionManager baselineTransactionManager;

	protected OrchidGroup saveOrchidGroup(OrchidGroup group) {
		return new TransactionTemplate(baselineTransactionManager).execute(status -> {
			var saved = orchidGroupRepository.saveAndFlush(group);
			if (saved.getStateRevision() == null) {
				FarmTestFixtures.baseline(baselineEntityManager, saved);
			}
			baselineEntityManager.flush();
			return saved;
		});
	}

}

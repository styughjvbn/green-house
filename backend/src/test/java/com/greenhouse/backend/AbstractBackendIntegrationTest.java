package com.greenhouse.backend;

import com.greenhouse.backend.farm.repository.BedZoneRepository;
import com.greenhouse.backend.farm.repository.HouseRepository;
import com.greenhouse.backend.farm.repository.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.MaterialRepository;
import com.greenhouse.backend.farm.repository.OrchidGroupRepository;
import com.greenhouse.backend.farm.repository.PhysicalBedRepository;
import com.greenhouse.backend.farm.repository.VarietyRepository;
import com.greenhouse.backend.work.repository.WorkTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
    protected WorkTypeRepository workTypeRepository;
    @Autowired
    protected VarietyRepository varietyRepository;
    @Autowired
    protected InboundRecordRepository inboundRecordRepository;
    @Autowired
    protected MaterialRepository materialRepository;
}

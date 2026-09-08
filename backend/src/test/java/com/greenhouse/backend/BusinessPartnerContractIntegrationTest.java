package com.greenhouse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.partner.application.BusinessPartnerInfo;
import com.greenhouse.backend.partner.application.BusinessPartnerLock;
import com.greenhouse.backend.partner.application.BusinessPartnerReader;
import com.greenhouse.backend.partner.domain.BusinessPartner;
import com.greenhouse.backend.partner.domain.PartnerType;
import com.greenhouse.backend.partner.repository.BusinessPartnerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BusinessPartnerContractIntegrationTest {

	@Autowired
	BusinessPartnerRepository partnerRepository;

	@Autowired
	BusinessPartnerReader partnerReader;

	@Autowired
	BusinessPartnerLock partnerLock;

	@Test
	void returnsAnImmutableCopyOfCurrentMasterData() {
		var partner = createPartner("기존 이름");
		var info = partnerReader.getActiveInfo(partner.getId());

		partner.update("새 이름", PartnerType.RETAIL, "대표", "연락처", "주소", "메모");

		assertThat(info).isEqualTo(new BusinessPartnerInfo(partner.getId(), "기존 이름", PartnerType.WHOLESALE, true, "대표",
				"연락처", "주소", "메모"));
		assertThat(partnerReader.getInfo(partner.getId())).isEqualTo(
				new BusinessPartnerInfo(partner.getId(), "새 이름", PartnerType.RETAIL, true, "대표", "연락처", "주소", "메모"));
	}

	@Test
	void allowsReadingAndLockingInactivePartnersForExistingTransactions() {
		var partner = createPartner("비활성 거래처");
		ReflectionTestUtils.setField(partner, "active", false);
		partnerRepository.flush();

		assertThat(partnerReader.getInfo(partner.getId()).active()).isFalse();
		assertThat(partnerLock.lockAll(List.of(partner.getId()))).extracting(BusinessPartnerInfo::active)
			.containsExactly(false);
		assertThatThrownBy(() -> partnerReader.getActiveInfo(partner.getId()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("비활성 거래처는 사용할 수 없습니다.");
	}

	@Test
	void locksDistinctPartnersInIdOrder() {
		var first = createPartner("첫 거래처");
		var second = createPartner("둘째 거래처");

		assertThat(partnerLock.lockAll(List.of(second.getId(), first.getId(), second.getId())))
			.extracting(BusinessPartnerInfo::id)
			.containsExactly(first.getId(), second.getId());
		assertThat(partnerLock.lockAll(List.of())).isEmpty();
	}

	@Test
	void bulkReadsReturnEachRequestedPartnerOnce() {
		var first = createPartner("일괄 조회 1");
		var second = createPartner("일괄 조회 2");

		var partners = partnerReader.getAllInfo(List.of(second.getId(), first.getId(), second.getId()));

		assertThat(partners).containsOnlyKeys(first.getId(), second.getId());
		assertThat(partners.get(first.getId()).name()).isEqualTo("일괄 조회 1");
		assertThatThrownBy(() -> partners.clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThat(partnerReader.getAllInfo(List.of())).isEmpty();
	}

	@Test
	void rejectsPartialBulkReadResults() {
		var partner = createPartner("일괄 조회 누락");
		assertThatThrownBy(() -> partnerReader.getAllInfo(List.of(partner.getId(), -1L)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void rejectsMissingPartnerReads() {
		assertThatThrownBy(() -> partnerReader.getInfo(-1L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void rejectsPartialLockResults() {
		var partner = createPartner("일부만 존재");
		assertThatThrownBy(() -> partnerLock.lockAll(List.of(partner.getId(), -1L)))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void requiresTheCallingUseCaseToOwnTheLockTransaction() {
		assertThatThrownBy(() -> partnerLock.lockAll(List.of(1L))).isInstanceOf(IllegalTransactionStateException.class);
	}

	private BusinessPartner createPartner(String name) {
		return partnerRepository
			.saveAndFlush(new BusinessPartner(name, PartnerType.WHOLESALE, "대표", "연락처", "주소", "메모"));
	}

}

package com.greenhouse.backend.farm.application.orchid;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import com.greenhouse.backend.audit.domain.AuditAction;
import org.junit.jupiter.api.Test;

class OrchidGroupAuditSupportTest {
	private final OrchidGroupAuditSupport support = new OrchidGroupAuditSupport(null, null);

	@Test
	void identicalSnapshotsHaveNoChanges() {
		var snapshot = snapshot(1L, 10, 1L, 2L, 3L);
		assertThat(support.detectChanges(snapshot, snapshot)).isEmpty();
	}

	@Test
	void detectsQuantityChange() {
		assertThat(support.detectChanges(snapshot(1L, 10, 1L, 2L, 3L), snapshot(1L, 11, 1L, 2L, 3L)))
				.containsExactly("quantity");
	}

	@Test
	void detectsNullToValueChange() {
		var before = new OrchidGroupAuditSnapshot(1L, null, null, 10, 1L, 2L, 3L, null, null, "정상");
		var after = new OrchidGroupAuditSnapshot(1L, 2, "4인치", 10, 1L, 2L, 3L, null, null, "정상");
		assertThat(support.detectChanges(before, after)).containsExactly("ageYear", "potSize");
	}

	@Test
	void detectsLocationChanges() {
		assertThat(support.detectChanges(snapshot(1L, 10, 1L, 2L, 3L), snapshot(1L, 10, 4L, 5L, 6L)))
				.containsExactly("houseId", "physicalBedId", "zoneId");
	}

	@Test
	void detectsMultipleChanges() {
		var before = snapshot(1L, 10, 1L, 2L, 3L);
		var after = new OrchidGroupAuditSnapshot(9L, 3, "5인치", 20, 1L, 2L, 3L,
				BigDecimal.ONE, BigDecimal.TWO, "주의");
		assertThat(support.detectChanges(before, after))
				.containsExactly("varietyId", "ageYear", "potSize", "quantity", "status");
	}

	@Test
	void classifiesActiveToInactiveStatusAsDeactivation() {
		var before = snapshot(1L, 10, 1L, 2L, 3L);
		for (String status : new String[]{"종료", "폐기", "판매 완료", "생성 취소"}) {
			var after = withStatus(before, status);
			assertThat(support.actionForCorrection(before, after)).isEqualTo(AuditAction.DEACTIVATED);
		}
	}

	@Test
	void keepsOrdinaryAndInactiveToInactiveCorrectionsAsUpdates() {
		var active = snapshot(1L, 10, 1L, 2L, 3L);
		assertThat(support.actionForCorrection(active, withStatus(active, "주의")))
				.isEqualTo(AuditAction.UPDATED);
		var inactive = withStatus(active, "종료");
		assertThat(support.actionForCorrection(inactive, withStatus(inactive, "폐기")))
				.isEqualTo(AuditAction.UPDATED);
	}

	private OrchidGroupAuditSnapshot snapshot(Long varietyId, int quantity, Long houseId, Long bedId, Long zoneId) {
		return new OrchidGroupAuditSnapshot(varietyId, 2, "4인치", quantity, houseId, bedId, zoneId,
				BigDecimal.ONE, BigDecimal.TWO, "정상");
	}

	private OrchidGroupAuditSnapshot withStatus(OrchidGroupAuditSnapshot snapshot, String status) {
		return new OrchidGroupAuditSnapshot(snapshot.varietyId(), snapshot.ageYear(), snapshot.potSize(),
				snapshot.quantity(), snapshot.houseId(), snapshot.physicalBedId(), snapshot.zoneId(),
				snapshot.startPosition(), snapshot.endPosition(), status);
	}
}

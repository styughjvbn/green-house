package com.greenhouse.backend.sales.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.greenhouse.backend.sales.api.partner.BusinessPartnerInfo;
import com.greenhouse.backend.sales.api.partner.BusinessPartnerQueryApi;
import com.greenhouse.backend.sales.api.partner.PartnerType;
import com.greenhouse.backend.sales.payment.api.PaymentTargetType;
import com.greenhouse.backend.sales.payment.repository.PartnerPaymentEventRepository;
import com.greenhouse.backend.sales.payment.spi.PaymentAllocationTargetPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentAllocationMetadataTest {
  @SuppressWarnings("unchecked")
  @Test
  void selectsTargetKindsForPartnerAndPreservesGlobalMetadata() {
    var partners = mock(BusinessPartnerQueryApi.class);
    PaymentAllocationTargetPort<Object> direct = mock(PaymentAllocationTargetPort.class);
    PaymentAllocationTargetPort<Object> auction = mock(PaymentAllocationTargetPort.class);
    when(direct.targetType()).thenReturn(PaymentTargetType.SALES_SLIP);
    when(auction.targetType()).thenReturn(PaymentTargetType.AUCTION_PROCEEDS);
    var reader =
        new PaymentReceiptReader(
            mock(PartnerPaymentEventRepository.class),
            mock(PaymentReceiptIntegrity.class),
            List.of(direct, auction),
            partners);
    for (var type : PartnerType.values()) {
      when(partners.getInfo(7L))
          .thenReturn(new BusinessPartnerInfo(7L, "거래처", type, true, null, null, null, null));
      assertThat(reader.metadata(7L).targetTypes())
          .containsExactly(
              type == PartnerType.AUCTION_HOUSE
                  ? PaymentTargetType.AUCTION_PROCEEDS
                  : PaymentTargetType.SALES_SLIP);
    }
    assertThat(reader.metadata(null).targetTypes())
        .containsExactlyInAnyOrder(
            PaymentTargetType.SALES_SLIP, PaymentTargetType.AUCTION_PROCEEDS);
  }
}

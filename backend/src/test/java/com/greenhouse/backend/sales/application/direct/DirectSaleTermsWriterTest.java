package com.greenhouse.backend.sales.application.direct;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.sales.application.document.DirectDocumentAccountingPort.Price;
import com.greenhouse.backend.sales.repository.direct.DirectSaleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class DirectSaleTermsWriterTest {
  @Test
  void quotesBeforeDocumentIdsExistWithoutTouchingPersistence() {
    var repository = mock(DirectSaleRepository.class);
    var reviews = mock(DirectSaleReviewReader.class);
    var writer = new DirectSaleTermsWriter(repository, reviews);
    var quoted = writer.quotePrices(List.of(new Price(null, 2, 1000), new Price(null, 1, 500)));
    assertThat(quoted.totalAmount()).isEqualTo(2500);
    assertThat(quoted.prices()).extracting(price -> price.amount()).containsExactly(2000, 500);
    assertThatThrownBy(
            () ->
                writer.quotePrices(
                    List.of(new Price(null, 1, Integer.MAX_VALUE), new Price(null, 1, 1))))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository, reviews);
  }
}

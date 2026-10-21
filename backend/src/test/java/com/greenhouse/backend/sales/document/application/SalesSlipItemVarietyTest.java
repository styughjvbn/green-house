package com.greenhouse.backend.sales.document.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.greenhouse.backend.farm.api.orchid.OrchidGroupQueryApi;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupState;
import com.greenhouse.backend.farm.api.variety.VarietySearchApi;
import com.greenhouse.backend.sales.document.application.command.*;
import java.time.Clock;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SalesSlipItemVarietyTest {
  final OrchidGroupQueryApi groups = mock(OrchidGroupQueryApi.class);
  final VarietySearchApi varieties = mock(VarietySearchApi.class);
  final SalesSlipAllocationFactory factory =
      new SalesSlipAllocationFactory(groups, Clock.systemUTC(), varieties);

  SalesSlipItemInput item(Long varietyId, List<SalesSlipAllocationInput> allocations) {
    return new SalesSlipItemInput(
        "작성 당시 이름", "작성 당시 속", null, 2, 100, null, allocations, null, null, varietyId);
  }

  @Test
  void recordedIdsAreVerifiedOnceWithoutReplacingHistoricalNamesEvenForInactiveVarieties() {
    when(varieties.findOptionsByIds(Set.of(42L)))
        .thenReturn(List.of(new VarietySearchApi.Option(42L, "현재 이름", "현재 속", false)));
    var requests = IntStream.range(0, 100).mapToObj(i -> item(42L, List.of())).toList();
    var items = factory.createRecordedItems(requests, null);
    assertThat(items)
        .allSatisfy(
            i -> {
              assertThat(i.getVarietyId()).isEqualTo(42L);
              assertThat(i.getItemName()).isEqualTo("작성 당시 이름");
              assertThat(i.getGenus()).isEqualTo("작성 당시 속");
            });
    verify(varieties, times(1)).findOptionsByIds(Set.of(42L));
    verifyNoInteractions(groups);
  }

  @Test
  void absentAndInvalidRecordedIdsAreRejected() {
    assertThatThrownBy(() -> factory.createRecordedItems(List.of(item(null, List.of())), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("품종을 선택");
    verifyNoInteractions(varieties, groups);
    when(varieties.findOptionsByIds(Set.of(999L))).thenReturn(List.of());
    assertThatThrownBy(() -> factory.createRecordedItems(List.of(item(999L, List.of())), null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void allocationsMatchByIdentityAcrossRenamesAndRejectDifferentVarieties() {
    var first = mock(OrchidGroupState.class);
    when(first.id()).thenReturn(1L);
    when(first.varietyId()).thenReturn(42L);
    when(first.varietyName()).thenReturn("현재 이름");
    when(groups.lockStates(List.of(1L))).thenReturn(Map.of(1L, first));
    var item =
        factory
            .createItems(List.of(item(42L, List.of(new SalesSlipAllocationInput(1L, 2)))))
            .getFirst();
    assertThat(item.getVarietyId()).isEqualTo(42L);
    assertThat(item.getItemName()).isEqualTo("작성 당시 이름");
    assertThatThrownBy(
            () ->
                factory.createItems(
                    List.of(item(43L, List.of(new SalesSlipAllocationInput(1L, 2))))))
        .isInstanceOf(IllegalArgumentException.class);
    var second = mock(OrchidGroupState.class);
    when(second.id()).thenReturn(2L);
    when(second.varietyId()).thenReturn(43L);
    when(second.varietyName()).thenReturn("현재 이름");
    when(first.varietyName()).thenReturn("작성 당시 이름");
    when(second.varietyName()).thenReturn("작성 당시 이름");
    when(groups.lockStates(List.of(1L, 2L))).thenReturn(Map.of(1L, first, 2L, second));
    assertThatThrownBy(
            () ->
                factory.createItems(
                    List.of(
                        item(
                            null,
                            List.of(
                                new SalesSlipAllocationInput(1L, 1),
                                new SalesSlipAllocationInput(2L, 1))))))
        .isInstanceOf(IllegalArgumentException.class);
    when(second.varietyId()).thenReturn(null);
    assertThatThrownBy(
            () ->
                factory.createItems(
                    List.of(
                        item(
                            null,
                            List.of(
                                new SalesSlipAllocationInput(1L, 1),
                                new SalesSlipAllocationInput(2L, 1))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("품종을 선택");
    var inferred =
        factory
            .createItems(List.of(item(null, List.of(new SalesSlipAllocationInput(1L, 2)))))
            .getFirst();
    assertThat(inferred.getVarietyId()).isEqualTo(42L);
  }
}

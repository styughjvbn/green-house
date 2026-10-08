package com.greenhouse.backend.work.domain.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.greenhouse.backend.work.api.operation.WorkSourceScopeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkOperationTitleTest {

  @Test
  void updatesTitleAfterTrimmingWhitespace() {
    WorkOperation operation = operation();

    operation.updateTitle("  새 작업명  ");

    assertThat(operation.getTitle()).isEqualTo("새 작업명");
  }

  @Test
  void rejectsBlankOrTooLongTitle() {
    WorkOperation operation = operation();

    assertThatThrownBy(() -> operation.updateTitle("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("작업명이 필요합니다.");
    assertThatThrownBy(() -> operation.updateTitle("가".repeat(151)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("작업명은 150자 이하여야 합니다.");
  }

  private WorkOperation operation() {
    return new WorkOperation(
        mock(WorkType.class),
        "기존 작업명",
        LocalDate.of(2026, 10, 1),
        null,
        WorkSourceScopeType.NONE,
        null,
        Map.of(),
        Map.of(),
        null,
        null,
        LocalDateTime.of(2026, 10, 1, 9, 0));
  }
}

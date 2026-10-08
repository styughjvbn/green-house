package com.greenhouse.backend.work.operation.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.ConflictException;
import com.greenhouse.backend.work.api.operation.WorkOperationView;
import com.greenhouse.backend.work.operation.domain.WorkCommandReceiptMembership;
import com.greenhouse.backend.work.operation.repository.WorkCommandReceiptMembershipRepository;
import com.greenhouse.backend.work.operation.repository.WorkCommandReceiptRepository;
import java.time.Clock;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class WorkCommandReceipts {

  private static final JsonMapper RESPONSES =
      JsonMapper.builder()
          .findAndAddModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final WorkCommandReceiptRepository repository;

  private final WorkCommandReceiptMembershipRepository membershipRepository;

  private final WorkRequestFingerprint fingerprints;

  private final Clock clock;

  public List<WorkOperationView> executeCreation(
      String scope, String key, Object request, Supplier<List<WorkOperationView>> action) {
    String identity = scope + ":" + normalizeKey(key);
    String fingerprint = fingerprints.calculate(request);
    int inserted = repository.claim(identity, fingerprint, TimeConfig.utcNow(clock));
    var receipt = repository.findForUpdate(identity);
    receipt.validate(fingerprint);
    if (receipt.getResultOperationIds() != null) {
      if (receipt.getResponseSnapshot() == null) {
        throw new ConflictException(
            "IDEMPOTENCY_REPLAY_UNAVAILABLE", "최초 응답이 없는 작업 요청입니다. 기존 작업을 조회해 주세요.");
      }
      try {
        return RESPONSES.readValue(
            receipt.getResponseSnapshot(), new TypeReference<List<WorkOperationView>>() {});
      } catch (JsonProcessingException exception) {
        throw new IllegalStateException("작업 생성 응답을 복원할 수 없습니다.", exception);
      }
    }
    if (inserted != 1) {
      throw new IllegalStateException("완료되지 않은 작업 요청 기록입니다.");
    }
    var views = action.get();
    var ids = views.stream().map(WorkOperationView::id).toList();
    try {
      receipt.completeCreation(ids, RESPONSES.writeValueAsString(views));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("작업 생성 응답을 보존할 수 없습니다.", exception);
    }
    repository.flush();
    saveMemberships(identity, ids);
    membershipRepository.flush();
    return views;
  }

  public List<Long> execute(String scope, String key, Object request, Supplier<List<Long>> action) {
    return execute(scope, key, request, action, true);
  }

  public List<Long> executeExisting(
      String scope, String key, Object request, Supplier<List<Long>> action) {
    return execute(scope, key, request, action, false);
  }

  private List<Long> execute(
      String scope, String key, Object request, Supplier<List<Long>> action, boolean creation) {
    String identity = scope + ":" + normalizeKey(key);
    String fingerprint = fingerprints.calculate(request);
    int inserted = repository.claim(identity, fingerprint, TimeConfig.utcNow(clock));
    var receipt = repository.findForUpdate(identity);
    receipt.validate(fingerprint);
    if (receipt.getResultOperationIds() != null) {
      return receipt.getResultOperationIds();
    }
    if (inserted != 1) {
      throw new IllegalStateException("완료되지 않은 작업 요청 기록입니다.");
    }
    receipt.complete(action.get());
    repository.flush();
    if (creation) {
      saveMemberships(identity, receipt.getResultOperationIds());
    }
    return receipt.getResultOperationIds();
  }

  private void saveMemberships(String identity, List<Long> ids) {
    membershipRepository.saveAll(
        ids.stream()
            .map(operationId -> new WorkCommandReceiptMembership(identity, operationId))
            .toList());
  }

  public static String normalizeKey(String key) {
    if (key == null || key.isBlank() || key.trim().length() > 100) {
      throw new IllegalArgumentException("멱등 키는 1~100자여야 합니다.");
    }
    return key.trim();
  }
}

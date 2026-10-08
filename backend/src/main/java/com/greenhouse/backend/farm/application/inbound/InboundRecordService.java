package com.greenhouse.backend.farm.application.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.greenhouse.backend.audit.domain.AuditAction;
import com.greenhouse.backend.common.application.RequestActorProvider;
import com.greenhouse.backend.common.config.TimeConfig;
import com.greenhouse.backend.common.exception.NotFoundException;
import com.greenhouse.backend.farm.api.orchid.CreateInboundOrchidGroupsMutationCommand;
import com.greenhouse.backend.farm.api.orchid.CreateOrchidGroupMutationItem;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationDetails;
import com.greenhouse.backend.farm.api.orchid.OrchidGroupMutationSources;
import com.greenhouse.backend.farm.application.orchid.mutation.OrchidGroupMutationEngine;
import com.greenhouse.backend.farm.domain.inbound.InboundRecord;
import com.greenhouse.backend.farm.domain.inbound.InboundStatus;
import com.greenhouse.backend.farm.domain.inbound.InboundType;
import com.greenhouse.backend.farm.domain.orchid.OrchidGroup;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordCancelRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordPottingVoidRequest;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordResponse;
import com.greenhouse.backend.farm.dto.inbound.InboundRecordUpdateRequest;
import com.greenhouse.backend.farm.repository.inbound.InboundCreationReceiptRepository;
import com.greenhouse.backend.farm.repository.inbound.InboundRecordRepository;
import com.greenhouse.backend.farm.repository.orchid.OrchidGroupRepository;
import com.greenhouse.backend.farm.structure.application.OrchidPlacementPolicy;
import com.greenhouse.backend.farm.structure.domain.BedZone;
import com.greenhouse.backend.farm.structure.repository.BedZoneRepository;
import com.greenhouse.backend.farm.variety.application.VarietyService;
import com.greenhouse.backend.farm.variety.domain.Variety;
import com.greenhouse.backend.work.api.effect.WorkMutationLink;
import com.greenhouse.backend.work.api.operation.InboundPottingOperationApi;
import com.greenhouse.backend.work.api.operation.InboundWorkOperationLifecycleApi;
import com.greenhouse.backend.work.api.operation.InboundWorkOperationRecordingApi;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class InboundRecordService {

  private static final String DEFAULT_ORCHID_STATUS = "정상";
  private static final JsonMapper RECEIPT_MAPPER =
      JsonMapper.builder()
          .findAndAddModules()
          .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private final InboundCreationReceiptRepository creationReceiptRepository;
  private final Clock clock;

  private final InboundRecordRepository inboundRecordRepository;

  private final VarietyService varietyService;

  private final BedZoneRepository bedZoneRepository;

  private final OrchidGroupRepository orchidGroupRepository;

  private final InboundWorkOperationRecordingApi inboundWorkOperationRecorder;

  private final InboundWorkOperationLifecycleApi inboundWorkOperationLifecycleService;

  private final OrchidPlacementPolicy orchidPlacementPolicy;

  private final InboundRecordFinder inboundRecordFinder;

  private final InboundWorkOperationRequestFactory workOperationRequestFactory;

  private final RequestActorProvider requestActorProvider;

  private final InboundRecordAuditSupport auditSupport;

  private final OrchidGroupMutationEngine mutationEngine;

  private final InboundRecordResponseAssembler responseAssembler;

  private final InboundPottingOperationApi pottingOperationService;

  public InboundRecordResponse create(InboundRecordCreateCommand request) {
    return createNew(request);
  }

  public InboundRecordResponse create(InboundRecordCreateCommand request, String idempotencyKey) {
    if (idempotencyKey == null) return createNew(request);
    if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
      throw new IllegalArgumentException("입고 생성 요청 키는 1~100자의 공백이 아닌 값이 필요합니다.");
    }
    String fingerprint = creationFingerprint(request);
    int inserted =
        creationReceiptRepository.claim(idempotencyKey, fingerprint, TimeConfig.utcNow(clock));
    var receipt = creationReceiptRepository.findForUpdate(idempotencyKey);
    receipt.validate(fingerprint);
    try {
      if (receipt.getResponseSnapshot() != null) {
        return RECEIPT_MAPPER.readValue(receipt.getResponseSnapshot(), InboundRecordResponse.class);
      }
      if (inserted != 1) throw new IllegalStateException("완료되지 않은 입고 생성 요청 기록입니다.");
      var response = createNew(request);
      receipt.complete(response.id(), RECEIPT_MAPPER.writeValueAsString(response));
      creationReceiptRepository.flush();
      return response;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("입고 생성 요청의 저장된 응답을 처리할 수 없습니다.", exception);
    }
  }

  private String creationFingerprint(InboundRecordCreateCommand request) {
    try {
      // Preserve explicit input values, including decimal scale and null/default distinctions.
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(RECEIPT_MAPPER.writeValueAsBytes(request)));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new IllegalStateException("입고 생성 요청의 지문을 계산할 수 없습니다.", exception);
    }
  }

  private InboundRecordResponse createNew(InboundRecordCreateCommand request) {
    InboundStatus status = resolveCreateStatus(request);
    validateCreate(request);
    Variety variety =
        varietyService.resolveInboundVariety(request.varietyId(), request.newVariety());
    InboundPlacementInput placement = request.placement();
    BedZone bedZone = placement == null ? null : findBedZone(placement.bedZoneId());
    InboundRecord inboundRecord =
        new InboundRecord(
            request.inboundDate(),
            request.inboundType(),
            variety,
            status,
            request.estimatedQuantity(),
            normalize(request.tempLocation()),
            request.pottingDueDate(),
            requestActorProvider.resolve(request.worker()),
            normalize(request.memo()));
    InboundRecord saved = inboundRecordRepository.save(inboundRecord);
    WorkMutationLink mutationLink = null;
    List<OrchidGroup> createdGroups = List.of();

    if (requiresPlacement(request.inboundType())) {
      OrchidPlacementPolicy.PlacementRange placementRange =
          orchidPlacementPolicy.resolveRange(
              bedZone, placement.startPosition(), placement.endPosition());
      var mutationCommand =
          new CreateInboundOrchidGroupsMutationCommand(
              OrchidGroupMutationSources.inbound(saved.getId(), "CREATE_PLACED_GROUP"),
              saved.getId(),
              List.of(
                  new CreateOrchidGroupMutationItem(
                      bedZone.getId(),
                      new OrchidGroupMutationDetails(
                          variety.getId(),
                          placement.quantity(),
                          placement.potSize(),
                          placement.ageYear(),
                          DEFAULT_ORCHID_STATUS,
                          placement.placementType(),
                          placement.trayCount(),
                          false,
                          placementRange.startPosition(),
                          placementRange.endPosition(),
                          request.memo()))),
              request.inboundDate(),
              "즉시 배치 입고");
      var mutation = mutationEngine.createFromInbound(mutationCommand);
      List<Long> orchidGroupIds =
          mutation.entries().stream().map(entry -> entry.orchidGroupId()).toList();
      createdGroups = orchidGroupRepository.findAllById(orchidGroupIds);
      if (createdGroups.size() != orchidGroupIds.size()) {
        throw new NotFoundException("생성된 난 묶음을 찾을 수 없습니다.");
      }
      mutationLink = new WorkMutationLink(mutation.mutationId(), mutation.correlationId());
      saved.markPlaced();
    } else {
      saved.markPottingPending(status);
    }
    inboundWorkOperationRecorder.record(
        workOperationRequestFactory.create(saved, createdGroups), mutationLink);
    // Final timestamps and cascaded IDs belong to the first response, not a later replay read.
    inboundRecordRepository.flush();
    return responseAssembler.assemble(inboundRecordFinder.find(saved.getId()));
  }

  public InboundRecordResponse update(Long inboundRecordId, InboundRecordUpdateRequest request) {
    InboundRecord inboundRecord = inboundRecordFinder.findForUpdate(inboundRecordId);
    Map<String, Object> before = auditSupport.snapshot(inboundRecord);
    inboundRecord.updateMetadata(
        request.inboundDate(),
        request.estimatedQuantity(),
        normalize(request.tempLocation()),
        request.pottingDueDate(),
        requestActorProvider.resolve(request.worker()),
        normalize(request.memo()));
    auditSupport.record(
        AuditAction.UPDATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
    return responseAssembler.assemble(inboundRecord);
  }

  public InboundRecordResponse cancel(Long inboundRecordId, InboundRecordCancelRequest request) {
    inboundWorkOperationLifecycleService.lockForInboundChange(inboundRecordId);
    InboundRecord inboundRecord = inboundRecordFinder.findForUpdate(inboundRecordId);
    if (inboundRecord.getStatus() == InboundStatus.CANCELED) {
      return responseAssembler.assemble(inboundRecord);
    }
    Map<String, Object> before = auditSupport.snapshot(inboundRecord);
    String reason = normalize(request.memo()) == null ? "입고 취소" : normalize(request.memo());
    String requestKey =
        resolveRequestKey(request.idempotencyKey(), "inbound-cancel", inboundRecordId);
    if (inboundRecord.getStatus() == InboundStatus.PLACED) {
      if (inboundRecord.getInboundType() == InboundType.FLASK_SEEDLING) {
        inboundWorkOperationLifecycleService.voidPottingForInboundRecord(
            inboundRecordId, childRequestKey(requestKey, "potting"), reason);
      } else {
        inboundWorkOperationLifecycleService.voidInboundRegistrationForCancellation(
            inboundRecordId, childRequestKey(requestKey, "registration"), reason);
      }
    }
    inboundRecord.requireCancellable();
    inboundWorkOperationLifecycleService.cancelForInboundRecord(inboundRecordId);
    inboundRecord.cancel(normalize(request.memo()));
    auditSupport.record(
        AuditAction.DEACTIVATED, inboundRecord, before, auditSupport.snapshot(inboundRecord));
    return responseAssembler.assemble(inboundRecord);
  }

  public InboundRecordResponse voidPotting(
      Long inboundRecordId, InboundRecordPottingVoidRequest request) {
    pottingOperationService.voidForInbound(
        inboundRecordId, request.idempotencyKey(), request.reason());
    return responseAssembler.assemble(inboundRecordFinder.find(inboundRecordId));
  }

  private void validateCreate(InboundRecordCreateCommand request) {
    if (request.varietyId() == null && request.newVariety() == null) {
      throw new IllegalArgumentException("품종을 선택하거나 새 품종을 입력해야 합니다.");
    }
    if (request.inboundType() == InboundType.FLASK_SEEDLING) {
      if (request.estimatedQuantity() == null) {
        throw new IllegalArgumentException("유리병 모종은 예상 수량이 필요합니다.");
      }
      if (request.placement() != null) {
        throw new IllegalArgumentException("유리병 모종 입고에는 즉시 배치 결과를 입력할 수 없습니다.");
      }
      return;
    }
    if (request.placement() == null) {
      throw new IllegalArgumentException("즉시 배치 입고는 난 묶음 배치 결과가 필요합니다.");
    }
  }

  private InboundStatus resolveCreateStatus(InboundRecordCreateCommand request) {
    if (request.inboundType() == InboundType.FLASK_SEEDLING) {
      return InboundStatus.POTTING_PENDING;
    }
    return InboundStatus.PLACED;
  }

  private boolean requiresPlacement(InboundType inboundType) {
    return inboundType != InboundType.FLASK_SEEDLING;
  }

  private BedZone findBedZone(Long bedZoneId) {
    if (bedZoneId == null) {
      throw new IllegalArgumentException("배치 구역이 필요합니다.");
    }
    return bedZoneRepository
        .findWithDetailsById(bedZoneId)
        .orElseThrow(() -> new NotFoundException("논리 구역을 찾을 수 없습니다."));
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String resolveRequestKey(String provided, String prefix, Long inboundRecordId) {
    String normalized = normalize(provided);
    String generated = prefix + "-" + inboundRecordId + "-" + UUID.randomUUID();
    return normalized == null
        ? generated.substring(0, Math.min(100, generated.length()))
        : normalized;
  }

  private String childRequestKey(String requestKey, String suffix) {
    String childSuffix = "-" + suffix;
    return requestKey.substring(0, Math.min(requestKey.length(), 100 - childSuffix.length()))
        + childSuffix;
  }
}

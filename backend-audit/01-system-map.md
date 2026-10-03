# Backend 시스템 지도

## 1. 조사 기준과 읽는 방법

- 조사일: 2026-10-03, Asia/Seoul. HEAD `08b50dc7`, 브랜치 `feature/work-correction-audit-events`의 작업 트리 기준.
- 전체 backend 파일 inventory와 주요 유스케이스의 실제 호출·저장·조회 본문을 확인했다. 품질 판정이나 리팩터링 제안은 하지 않는다.
- 이 문서의 동작 설명은 정적 코드 관찰이다. 테스트의 존재·검증 의도와 테스트 실행 성공은 구분한다. 이번 단계에서 테스트/애플리케이션/DB/CLI를 실행하지 않았다.
- main 경로의 기준은 `backend/src/main/java/com/greenhouse/backend/`, test 경로의 기준은 `backend/src/test/java/com/greenhouse/backend/`다.
- 클래스명은 동일 이름의 `.java` 파일을 의미한다. 본문 심볼 링크와 뒤의 Entity/Repository inventory에서 실제 파일로 이동할 수 있다.
- 출발 문서: `docs/00-index.md`, `01-overview.md`, `02-domain-model.md`, `04-architecture.md`, `06-api-guide.md`. API 색인·정책·기능 문서·ADR은 의도 확인용으로 사용하고 아래 구현과 별도로 대조했다.

조사 inventory: main Java 파일 635개, `@Entity` 43개, Repository 타입 파일 54개와 Repository 패키지 projection/row 9개. 파일 수는 구조 확인 범위이며 품질 지표로 사용하지 않는다.

## 2. 실행·구성

| 요소 | 실제 구성과 근거 |
| --- | --- |
| 애플리케이션 | [BackendApplication.java](../backend/src/main/java/com/greenhouse/backend/BackendApplication.java)의 Spring Boot 진입점. `backend/settings.gradle.kts`에 단일 root project `backend`; 13개 모듈은 Java package 경계이며 별도 Gradle subproject가 아님 |
| 빌드 | `backend/build.gradle.kts`: Java 21, Spring Boot 4.1.0, JPA/Web MVC/Validation/Security/Actuator/Flyway, QueryDSL 5.1.0, Springdoc 3.0.3, Lombok |
| 저장소 | `backend/src/main/resources/application.yml`: 기본 PostgreSQL datasource, Flyway 활성, JPA `validate`, OSIV 비활성, UTC JDBC, JDBC batch size 기본 50 및 insert/update 정렬 |
| 시간 | [TimeConfig.farmClock()](../backend/src/main/java/com/greenhouse/backend/common/config/TimeConfig.java)은 UTC Clock, `farmToday()`는 Asia/Seoul 업무일, `utcNow()`는 UTC 저장값, JPA auditing provider도 같은 Clock 사용 |
| 기본 테스트 | `application-test.properties`: H2 PostgreSQL mode, JPA create-drop, Flyway 비활성. `application-test.yml`: auth 비활성 |
| 실제 DB 테스트 | `application-e2e.yml`: Flyway 활성/JPA validate. [WorkE2ETestBase.java](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkE2ETestBase.java): PostgreSQL 18-alpine Testcontainers, RANDOM_PORT HTTP, e2e profile |
| API 명세 | `docs/api/slices/*.openapi.yaml` 15개. Controller tag로 분할된 저장 명세이며 runtime 환경별 노출과 별도로 확인해야 함 |
| 기동 시 조회·쓰기 | [OrchidGroupLedgerWriterStartupGuard.run()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerWriterStartupGuard.java)의 기동 검증; [AuctionSettlementInitializer.run()](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementInitializer.java) → [AuctionSettlementService.rebuildExistingResults()](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java)의 정산 쓰기 |
| 비 HTTP 실행 | Gradle `orchidLedgerReconcile`, `orchidLedgerCutover`, `orchidStateChainMigrate`, `orchidLedgerStartupVerify`가 각각 전용 CLI main class 실행 |

## 3. 모듈 구조와 책임

실제 top-level package는 다음 13개다. [ModularArchitectureTests.MODULES](../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java)/`ALLOWED_DEPENDENCIES`와 main 소스 참조를 대조했다.

| 모듈 | 책임 | 주요 구현 지점 |
| --- | --- | --- |
| common | 성공/실패·페이지 응답, exception handler, 공통 Entity 시간, 요청 작업자, Clock/CORS/QueryDSL/OpenAPI 설정 | [ApiResponse](../backend/src/main/java/com/greenhouse/backend/common/api/ApiResponse.java), [ErrorResponse](../backend/src/main/java/com/greenhouse/backend/common/api/ErrorResponse.java), [ErrorResponseWriter](../backend/src/main/java/com/greenhouse/backend/common/api/ErrorResponseWriter.java), [PageRequests](../backend/src/main/java/com/greenhouse/backend/common/api/PageRequests.java), [GlobalExceptionHandler](../backend/src/main/java/com/greenhouse/backend/common/exception/GlobalExceptionHandler.java), [BaseEntity](../backend/src/main/java/com/greenhouse/backend/common/domain/BaseEntity.java), [RequestActorProvider](../backend/src/main/java/com/greenhouse/backend/common/application/RequestActorProvider.java), [TimeConfig](../backend/src/main/java/com/greenhouse/backend/common/config/TimeConfig.java) |
| audit | 변경 이벤트 DTO → DB 감사 이벤트, HTTP 식별자·실행자 맥락, no-op 변경 검사 | [AuditEventWriter.record()/recordChanges()](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditEventWriter.java), [AuditRequestContext.current()](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditRequestContext.java), [JpaAuditRecorder.record()](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java), [RequestIdFilter](../backend/src/main/java/com/greenhouse/backend/audit/application/RequestIdFilter.java) |
| auth | 계정 인증, 서버 세션·쿠키, API 접근 제어, runtime context, demo filter 조립 | [AuthService.login()/logout()](../backend/src/main/java/com/greenhouse/backend/auth/AuthService.java), [SessionCookieWriter](../backend/src/main/java/com/greenhouse/backend/auth/SessionCookieWriter.java), [SessionCookieRefreshFilter](../backend/src/main/java/com/greenhouse/backend/auth/SessionCookieRefreshFilter.java), [SecurityConfig.securityFilterChain()](../backend/src/main/java/com/greenhouse/backend/auth/SecurityConfig.java), [DefaultAccountsAutoConfiguration](../backend/src/main/java/com/greenhouse/backend/auth/DefaultAccountsAutoConfiguration.java) |
| demo | demo 인증 주체, 차단 경로, 프로세스 내 클라이언트 요청 횟수·본문 크기 제한 | [DemoAuthenticationFilter](../backend/src/main/java/com/greenhouse/backend/demo/DemoAuthenticationFilter.java), [DemoProtectionFilter.doFilterInternal()](../backend/src/main/java/com/greenhouse/backend/demo/DemoProtectionFilter.java), [DemoRequestPolicy.blocks()](../backend/src/main/java/com/greenhouse/backend/demo/DemoRequestPolicy.java), [DemoRequestLimiter.record()](../backend/src/main/java/com/greenhouse/backend/demo/DemoRequestLimiter.java) |
| farm | 농장 구조·배치, 난 묶음 현재 상태·원장, 품종·입고·자재, 사용자/자동 그룹, Work port 구현과 구조 변환·계보 | [FarmQueryService](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/FarmQueryService.java), [FarmStatusService](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmStatusService.java), [OrchidGroupCommandService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java), [OrchidGroupMutationEngine](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java), [InboundRecordService](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java), Farm adapter/handler |
| work | 작업 유형, 계획·대상 snapshot, 실행·효과, 상태 전이·취소·보정, 요청 접수, 이력·관계·그래프 | [WorkOperationPlanService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [WorkOperationProgressService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java), [WorkEffectProcessor](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), [WorkOperationVoidService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java), [WorkOperationCorrectionService](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java) |
| partner | 거래처 기준정보·검색·현재 값 계약·잠금·감사 | [BusinessPartnerService](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerService.java), [BusinessPartnerReader](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java), [BusinessPartnerLock.lockAll()](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerLock.java) |
| sales | 일반/경매 전표·품목·배분, 예약·출고·복구, 판매 snapshot·재고 이동, 일반 입금, 판매 집계 | [SalesSlipCreationService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java), [SalesSlipUpdateService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java), [SalesSlipStatusService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java), [SalesSlipInventoryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java), [SalesPaymentService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [SalesQueryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java) |
| auction | 출하·lot 생성과 조건부 제거, 경매 시도·결과·반환·수량·상태 이력, Auction 읽기 계약 | [AuctionShipmentCreator](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java), [AuctionShipmentLifecycleService](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentLifecycleService.java), [AuctionTrackingService](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java), [AuctionDataReader](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionDataReader.java) |
| settlement | 정산 설정·예상 입금일, 경매 정산·행 snapshot, 입금 이벤트 원장·거래처 잔액, 경매 입금 | [AuctionSettlementService](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java), [PaymentService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java), [PaymentLedgerService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java), [PartnerBalanceService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java), [PartnerSettlementSettingsService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerSettlementSettingsService.java) |
| dashboard | Farm 집계의 운영 요약 조립 | [DashboardQueryService.getSummary()](../backend/src/main/java/com/greenhouse/backend/dashboard/application/DashboardQueryService.java) |
| analytics | 기간 검증, Sales/Farm/Work/Partner/Settlement 집계의 분석 응답 조립 | [AnalyticsQueryService.getSalesAnalytics()/getPartnerAnalytics()/getWorkAnalytics()](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java) |
| print | Sales 문서·페이지 계약을 출력 데이터로 제공 | [PrintQueryService.getPrintableSalesSlips()/getSalesSlipPrintData()](../backend/src/main/java/com/greenhouse/backend/print/application/PrintQueryService.java) |

Farm의 기능 패키지는 `structure`, `status`, `orchid`(하위 `mutation` 포함), `collection`, `inbound`, `variety`, `material`, `transformation`이다. Work의 application/domain/dto는 `operation`, `target`, `effect`, `correction`으로 구분된다. Auth/Demo/Common은 업무 모듈과 동일한 계층 폴더 구조를 강제하지 않는 구성이다.

### 주요 도메인 연결

- Farm: `House → PhysicalBed → BedZone → OrchidGroup`, [BedZoneCapacity](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/BedZoneCapacity.java)는 구역 설정. [OrchidGroup](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java)이 Variety/InboundRecord에 연결됨. 별도 BedZoneSegment Entity는 없고 숫자 배치 범위를 보관.
- 사용자 그룹: `OrchidGroupCollection → OrchidGroupCollectionMember`; 탈퇴 시각을 갖는 영속 소속. 자동 그룹은 [DerivedOrchidGroupService.getGroups()/getMembers()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DerivedOrchidGroupService.java)의 계산 결과이며 영속 Entity가 아님.
- 원장: `OrchidGroupMutation → OrchidGroupMutationEntry`, Mutation 사이 [OrchidGroupMutationRelation](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutationRelation.java), 전환 상태 [OrchidGroupLedgerCoverage](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupLedgerCoverage.java). 현재 행과 전체 snapshot/revision 이력의 역할이 구분됨.
- Work: `WorkType → WorkOperation → WorkOperationTarget → WorkTargetExecution`; `WorkAppliedEffect → WorkEffectOrchidGroup`이 실행의 SOURCE/RESULT 연결. WorkCommandReceipt와 membership은 생성 접수·역방향 관계 조회, WorkOperationCorrection/WorkCorrectionReceipt는 보정 감사와 접수.
- Sales: `SalesSlip → SalesSlipItem → SalesSlipItemAllocation → SalesOrchidGroupSnapshot`; [SalesInventoryMovement](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesInventoryMovement.java)는 배분별 예약·출고 사실. [SalesSlipDailySequence](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipDailySequence.java)는 일별 번호 카운터 모델.
- Auction: `AuctionShipment → AuctionShipmentLot → AuctionAttempt → AuctionResultLine`, [AuctionLotStatusHistory](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionLotStatusHistory.java)는 lot 상태 이력.
- Settlement: `AuctionSettlement → AuctionSettlementLine`; [PartnerSettlementSettings](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java), [PartnerPaymentEvent](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerPaymentEvent.java), [PartnerBalanceSummary](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerBalanceSummary.java)가 설정·입금 원장·현재 잔액을 소유.
- 모듈 간 주요 연결은 scalar ID다. Sales의 partner/orchid/shipment ID, Settlement의 result/lot/partner ID, Work의 orchid/inbound/mutation ID가 대표적이며 기존 DB FK의 존재는 JPA 객체 연관과 별개다.

전체 Entity 파일과 테이블은 아래 inventory에 수록한다. DTO 필드·enum·required 목록은 이 지도에 복제하지 않는다.

## 4. 주요 Application Service와 Repository 역할

| 기능 | Application 진입점·조율자 | 주요 저장소/조회 구현 |
| --- | --- | --- |
| 구조·현황 | [FarmQueryService.getHouses()/getPhysicalBeds()/getBedZones()/getOrchidGroups()](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/FarmQueryService.java), [FarmStatusService.getMap()/getOrchidManagementViewport()/getZoom()](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmStatusService.java) | [HouseRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/HouseRepository.java), [PhysicalBedRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/PhysicalBedRepository.java), [BedZoneRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneRepository.java), [OrchidGroupRepository.findMapRows()/findByPhysicalBedIdInOrderByLocation()](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) |
| 배치 프로필 | [BedPlacementProfileService](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/BedPlacementProfileService.java), [OrchidPlacementPolicy](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/OrchidPlacementPolicy.java), [BedPlacementProfilePolicy](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/BedPlacementProfilePolicy.java) | [BedZoneRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneRepository.java); BedZoneCapacity는 구역 aggregate에서 관리 |
| 난 묶음 명령 | [OrchidGroupCommandService.create()/update()/updateBatch()/delete()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java), [OrchidGroupReconciliationService.reconcile()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReconciliationService.java) | [OrchidGroupRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java), Mutation Engine |
| 원장 쓰기·읽기 | [OrchidGroupMutationEngine](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java), [OrchidGroupMutationRecorder](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationRecorder.java), [OrchidGroupMutationQueryService.getMutations()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationQueryService.java), [OrchidGroupMutationGraphQueryService.getGraph()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationGraphQueryService.java) | Mutation/Entry/Relation/Coverage Repository, [OrchidGroupWriteFenceRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupWriteFenceRepository.java)의 JdbcTemplate |
| 그룹 | [OrchidGroupCollectionService.create()/addMembers()/removeMember()/archive()/getCollections()](../backend/src/main/java/com/greenhouse/backend/farm/application/collection/OrchidGroupCollectionService.java), [DerivedOrchidGroupService](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DerivedOrchidGroupService.java) | Collection/Member Repository, [OrchidGroupRepository.findDerivedGroupCandidates()](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) |
| 품종·자재 | [VarietyService](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyService.java), [VarietyResponseAssembler](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyResponseAssembler.java), [MaterialService](../backend/src/main/java/com/greenhouse/backend/farm/application/material/MaterialService.java) | [VarietyRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyRepository.java)/[VarietyRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyRepositoryImpl.java), [MaterialRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/material/MaterialRepository.java)/[MaterialRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/farm/repository/material/MaterialRepositoryImpl.java), 코드 sequence 조회 |
| 입고 | [InboundRecordService.create()/update()/cancel()/voidPotting()](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java), [InboundRecordQueryService](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordQueryService.java), [InboundPottingService.potting()](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java) | [InboundRecordRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/inbound/InboundRecordRepository.java), [InboundRecordFinder](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordFinder.java), 결과 group/entry 일괄 조회 |
| Work 유형·계획 | [WorkTypeService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkTypeService.java), [WorkOperationPlanService.preview()/create()/createBatch()/createCompletedRecord()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java), [WorkOperationAggregateCreator](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java) | [WorkTypeRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkTypeRepository.java), Operation/Target/Execution Repository |
| Work 실행 | [WorkOperationProgressService.completeTarget()/endRemaining()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java), [StructureChangeExecutionService.execute()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java), [ImmediateWorkExecutionService.execute()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java), [InboundPottingOperationService.executeNow()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java) | Execution/Effect/EffectOrchidGroup Repository, [WorkEffectProcessor](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java), Farm handler/strategy |
| 즉시 기록 | [StructureChangeRecordService.createStructureChangeRecord()/createStructureChangeRecords()/createDiscardRecord()/createInboundPottingRecord()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java), [DiscardRecordService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/DiscardRecordService.java), [InboundWorkOperationRecorder.record()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundWorkOperationRecorder.java) | 기존 계획·실행기, [WorkCommandReceipts.execute()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java)와 Receipt Repository |
| Work 취소·보정 | [WorkOperationVoidService.cancelOperation()/cancelBatch()/voidOperation()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java), [WorkOperationCorrectionService.create()](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java), [WorkOperationDateCorrectionService.correct()](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationDateCorrectionService.java) | Operation/Effect/Execution/Correction/CorrectionReceipt Repository, Farm port 구현 |
| Work 조회 | [WorkOperationQueryService.search()/get()/getCalendar()/getWorkHistory()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java), [WorkOperationDetailService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java), [WorkOperationGraphQueryService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java), [WorkOperationRelationQueryService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationRelationQueryService.java) | Operation/Target/Execution/Effect/Correction/Membership Repository, summary/response/detail assembler |
| 거래처 | [BusinessPartnerService.create()/update()/getPartnerPage()/getOptions()](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerService.java), [BusinessPartnerReader.getAllInfo()/findMatchingIds()](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerReader.java), [BusinessPartnerLock.lockAll()](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerLock.java) | [BusinessPartnerRepository](../backend/src/main/java/com/greenhouse/backend/partner/repository/BusinessPartnerRepository.java)와 QueryDSL custom implementation |
| 판매 | [SalesSlipCreationService.create()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java), [SalesSlipUpdateService.update()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java), [SalesSlipStatusService.updateStatus()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java), [SalesSlipOutboundService.complete()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java), [SalesQueryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java) | [SalesSlipRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepository.java)/[SalesSlipRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepositoryImpl.java), Allocation/Movement Repository, [SalesSlipNumberRepository.nextDailySequence()](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipNumberRepository.java) JDBC |
| Auction | [AuctionTrackingService.getLots()/addResult()/confirmReturn()/adjust()/changeStatus()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java), [AuctionShipmentCreator.create()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java), [AuctionDataReader](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionDataReader.java) | Shipment/Lot/Attempt/ResultLine/StatusHistory Repository, Lot QueryDSL implementation |
| 정산·입금 | [AuctionSettlementService.rebuild()/rebuildExistingResults()](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java), [SalesPaymentService.confirmPayment()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java), [PaymentService.confirmAuctionPayment()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java), [PaymentLedgerService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java), [PartnerBalanceService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java), [PartnerSettlementSettingsService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerSettlementSettingsService.java) | Settlement/PaymentEvent/Balance/Settings Repository |
| 집계·출력 | [FarmMetricsReader](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java), [WorkOperationMetricsReader](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMetricsReader.java), [SalesMetricsReader](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesMetricsReader.java), [AnalyticsQueryService](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java), [DashboardQueryService](../backend/src/main/java/com/greenhouse/backend/dashboard/application/DashboardQueryService.java), [PrintQueryService](../backend/src/main/java/com/greenhouse/backend/print/application/PrintQueryService.java) | 소유 모듈의 scalar 집계/QueryDSL, 읽기 application 계약 |

Spring Data JPA 인터페이스 외에도 custom Repository/Impl과 JDBC 저장소가 있다. [SalesSlipNumberRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipNumberRepository.java)와 [OrchidGroupWriteFenceRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupWriteFenceRepository.java)를 JPA interface inventory에서 빠뜨리지 않도록 구분했다. snapshot·정산 행처럼 root에서 cascade 관리되는 Entity에는 독립 Repository가 없을 수 있다.

## 5. 모듈 간 의존 방향

### 소스 의존 방향

아래는 main 소스의 모듈 참조 방향이다. 테스트의 허용 집합만 옮긴 것이 아니다.

| 호출/참조 모듈 | 참조 대상 |
| --- | --- |
| common | 다른 top-level 업무 모듈 참조 없음 |
| audit | 현재 main 소스에서 다른 top-level 모듈 참조 없음. architecture 허용 집합에는 common이 있음 |
| demo | common |
| auth | common, demo |
| work | common |
| farm | common, audit, work |
| partner | common, audit |
| auction | common, partner |
| settlement | common, audit, auction, partner |
| sales | common, audit, farm, partner, auction, settlement |
| dashboard | common, farm |
| analytics | common, farm, work, sales, partner, settlement |
| print | common, sales |

### 업무 런타임 호출 방향

```text
Work application → Work port/handler → Farm 구현 → Mutation Engine
Farm 명령/입고 → Work application (즉시 실행·입고 기록·취소 조율)
Sales application → Farm Engine / Partner application / Auction application / Settlement application
Settlement application → AuctionDataReader / Partner application
Analytics → Sales/Farm/Work 집계 + Partner/Settlement 읽기 계약
Dashboard → FarmMetricsReader
Print → SalesQueryService
업무 감사 → AuditEventWriter → AuditRecorder → JpaAuditRecorder
```

Work의 런타임 Farm 호출은 Work가 정의한 인터페이스를 통한 주입이다. 따라서 소스 의존 `farm → work`와 런타임 `work → Farm 구현`을 같은 그래프로 취급하지 않는다. 이 지도는 테스트 실행으로 순환 부재를 판정한 결과가 아니다.

## 6. 주요 port와 adapter

| 계약 소유자·port | 실제 구현/consumer | 역할과 핵심 메서드 |
| --- | --- | --- |
| Work [WorkTargetResolver](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkTargetResolver.java) | Farm [FarmWorkTargetResolver](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java) | 범위·사용자/자동 그룹·직접 선택 해석 `resolve()`, 현재 대상 `getCurrent()`, 등록 잠금 `lockAndValidateActive()` |
| Work [InboundPottingPlanGateway](../backend/src/main/java/com/greenhouse/backend/work/application/target/InboundPottingPlanGateway.java) | Farm [FarmInboundPottingPlanGateway](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/FarmInboundPottingPlanGateway.java) | 입고 후보·현재 값, 쓰기용 resolve, 잠금, 계획 상태 반영 `resolveForUpdate()/lockForPottingExecution()/markPottingPlanned()/closePottingPlan()` |
| Work [WorkEffectHandler](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectHandler.java) | Work [RecordOnlyWorkHandler](../backend/src/main/java/com/greenhouse/backend/work/application/effect/RecordOnlyWorkHandler.java); Farm [MovementWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/MovementWorkHandler.java), [DiscardWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DiscardWorkHandler.java), [RepotWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotWorkHandler.java), [DivideWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/DivideWorkHandler.java), [MergeWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MergeWorkHandler.java), [InboundPottingExecutor](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingExecutor.java), [ReconciliationWorkHandler](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/ReconciliationWorkHandler.java) | `supports()/effectKind()/execute()`; WorkEffectProcessor가 code 우선/template fallback으로 선택하고 효과 저장은 WorkEffectStore가 담당 |
| Work [WorkExecutionReferenceGateway](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkExecutionReferenceGateway.java) | Farm [FarmWorkExecutionReferenceGateway](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkExecutionReferenceGateway.java) | 상세 표시용 품종/위치 batch `varietyNames()/locations()` |
| Work [WorkCorrectionPort](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionPort.java) | Farm [FarmWorkCorrectionAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java) | `correct()`에서 결과 참조·사용 검사, 날짜 보정·typed Mutation 조합 |
| Work [StructureChangeVoidPort](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeVoidPort.java) | Farm [FarmStructureChangeVoidAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeVoidAdapter.java) | 조회/잠금 검사 `inspect()/inspectForUpdate()`, 단건·일괄 보상 `compensate()/compensateBatch()` |
| Work [PottingVoidPort](../backend/src/main/java/com/greenhouse/backend/work/application/operation/PottingVoidPort.java) | Farm [FarmPottingVoidAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/FarmPottingVoidAdapter.java) | 포트 생성 Mutation 검사·보상 및 입고 재개 `inspect()/inspectForUpdate()/compensate()` |
| Work [WorkOperationMutationGraphPort](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMutationGraphPort.java) | Farm [FarmWorkOperationMutationGraphAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/FarmWorkOperationMutationGraphAdapter.java) | `load()`로 Mutation/revision/lineage fragment 제공 |
| Farm [OrchidGroupUsageInspector](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupUsageInspector.java) | Farm [FarmOrchidGroupUsageInspector](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmOrchidGroupUsageInspector.java), [WorkOrchidGroupUsageAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/WorkOrchidGroupUsageAdapter.java); Sales [SalesOrchidGroupUsageInspector](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupUsageInspector.java) | 생성 취소·보정·보상 시 입고/작업/판매 외부 참조 검사 `inspect()`; Work adapter는 Work application inspector 사용 |
| Farm [OrchidGroupLedgerRehearsalInspector](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerRehearsalInspector.java) | Sales [SalesOrchidGroupLedgerRehearsalInspector](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesOrchidGroupLedgerRehearsalInspector.java) | 예약·활성 배분·재고 이동의 대사 `inspect()`; Work 쪽은 [WorkOrchidGroupLedgerRehearsalInspector](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkOrchidGroupLedgerRehearsalInspector.java) application 계약으로 별도 호출 |
| Audit [AuditRecorder](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditRecorder.java) | [JpaAuditRecorder](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java) | `record()`가 호출자 transaction을 요구하고 AuditEventEntity 저장 |
| Spring Security `UserDetailsService` | 기본 `InMemoryUserDetailsManager`; 사용자 Bean 대체 가능 | [DefaultAccountsAutoConfiguration.userDetailsService()](../backend/src/main/java/com/greenhouse/backend/auth/DefaultAccountsAutoConfiguration.java)는 Bean 부재 조건으로 등록 |

Farm 내부 [StructureChangeStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategy.java) 구현은 [MovementStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MovementStrategy.java), [RepotStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/RepotStrategy.java), [DivideStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/DivideStrategy.java), [MergeStrategy](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/MergeStrategy.java)이며 [StructureChangeStrategyRegistry](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeStrategyRegistry.java)가 구성한다. Typed Engine command는 application 계약이고 외부 HTTP/은행/경매장 연동 adapter로 해석하지 않는다. 현재 확인한 auction/payment 경로는 수동 API·DB 처리이며 자동 은행/경매 수신 호출 경로는 노출된 Controller에 없다.

## 7. 주요 write path

### 7.1 난 묶음 생성·상세 수정·생성 취소

- [OrchidGroupCommandController](../backend/src/main/java/com/greenhouse/backend/farm/controller/orchid/OrchidGroupCommandController.java) → [OrchidGroupCommandService.create()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupCommandService.java) → [OrchidGroupMutationEngine.create()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) → 구역 잠금·배치 검증 → [OrchidGroupMutationRecorder.start()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationRecorder.java) → group/Entry 저장 → [OrchidGroupAuditSupport.record()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupAuditSupport.java).
- `update()/updateBatch()`는 현재 상세와 요청을 비교하고 변경 시 `updateDetails()` 호출 후 전후 감사를 저장한다. batch는 단일 public application transaction에서 각 항목을 처리한다.
- `delete()`는 group 잠금 → [WorkOrchidGroupUsageInspector.hasUncanceledReference()](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkOrchidGroupUsageInspector.java)와 Farm/Sales usage inspector 검사 → `cancelCreation()`. Controller의 DELETE가 물리 삭제라는 뜻은 아니다.
- [OrchidGroupReconciliationService.reconcile()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReconciliationService.java) → [ImmediateWorkExecutionService.executeForTarget()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/ImmediateWorkExecutionService.java) → [ReconciliationWorkHandler.execute()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/ReconciliationWorkHandler.java) → Engine `reconcile()` → Work 효과와 완료 기록.

### 7.2 계획과 기록형 대상 완료

- [WorkOperationController](../backend/src/main/java/com/greenhouse/backend/work/controller/WorkOperationController.java) → [WorkOperationPlanService.create()/createBatch()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationPlanService.java) → [WorkTargetResolver.resolve()](../backend/src/main/java/com/greenhouse/backend/work/application/target/WorkTargetResolver.java) → [WorkOperationAggregateCreator.createForOrchidGroups()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationAggregateCreator.java) → 등록 대상 잠금·활성 재검증 → Operation/Target/Execution 저장.
- `createCompletedRecord()`는 동일한 대상 확정 후 [WorkEffectProcessor.applyNew()](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java) → handler 실행 → [WorkEffectStore.save()](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java) → execution 완료와 operation 완료.
- 진행 중 대상 완료는 [WorkOperationProgressService.completeTarget()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java)에서 작업·대상 실행을 확인하고 [WorkEffectProcessor](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java)를 호출한다. 기존 효과는 [WorkEffectStore.find()/validateReplay()](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectStore.java)로 처리하며 새로운 효과와 대상 상태 변경은 같은 최상위 transaction에 참여.

### 7.3 구조 변경·자리 이동·폐기

- [StructureChangeExecutionService.execute()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeExecutionService.java) → [WorkEffectProcessor.applyBatch()](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectProcessor.java) → Farm handler → [StructureChangeExecutor.execute()](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/StructureChangeExecutor.java) → strategy 검증 → [BatchStructureTransformationExecutor.execute()](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/BatchStructureTransformationExecutor.java) → Engine `transform()` 또는 `moveAll()` → Work 효과·SOURCE/RESULT·누적 작업 수량 기록.
- 명시적인 1:1 전량 위치 변경은 `isIdentityPreservingMovement()`/`moveExistingGroups()` 분기로 기존 ID를 유지한다. 그 외 N:M 변환은 result 그룹을 생성하고 원본을 차감하며 실행 회차를 Work 효과로 연결.
- 이동의 잔류 폐기는 [MovementQuantityAllocator](../backend/src/main/java/com/greenhouse/backend/work/application/effect/MovementQuantityAllocator.java)와 [DiscardRecordService.createForMovement()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/DiscardRecordService.java)가 배분·연관 작업을 만들고 [DiscardWorkHandler.execute()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/DiscardWorkHandler.java) → Engine `discard()`로 처리.
- 즉시 기록은 [StructureChangeRecordService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/StructureChangeRecordService.java)가 계획·실행·완료를 한 transaction에서 조합한다. 단순 이동과 Farm의 분갈이 호환 facade도 Work 실행 경로를 사용한다.
- [OrchidGroupLineageService.record()](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupLineageService.java)는 직접 계보를 저장하며 복수 원본 실행의 SOURCE/RESULT 연결은 [WorkEffectOrchidGroup](../backend/src/main/java/com/greenhouse/backend/work/domain/effect/WorkEffectOrchidGroup.java)이 보관한다.

### 7.4 입고·포트 실행·취소

- [InboundRecordController](../backend/src/main/java/com/greenhouse/backend/farm/controller/inbound/InboundRecordController.java) → [InboundRecordService.create()](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java) → [VarietyService.resolveInboundVariety()](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyService.java) → 입고 저장. 즉시 배치 유형은 Engine `createFromInbound()` → 결과 group 연결 → [InboundWorkOperationRecorder.record()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundWorkOperationRecorder.java)에 Mutation link 전달. 유리병 모종은 포트 대기로 기록.
- [InboundPottingPlanService.create()/createBatch()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingPlanService.java) → gateway의 입고 잠금·대상 확정·계획 상태 변경.
- [InboundPottingOperationService.executeNow()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundPottingOperationService.java)는 접수를 확인하고 기존 활성 계획의 대상을 실행하거나 새 계획을 만든다. [WorkOperationProgressService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java) → [InboundPottingExecutor](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingExecutor.java) → [InboundPottingService.potting()](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundPottingService.java) → Engine `createFromInbound()` → 입고 placed 및 Work 효과·대상 완료.
- [InboundRecordService.cancel()/voidPotting()](../backend/src/main/java/com/greenhouse/backend/farm/application/inbound/InboundRecordService.java) → [InboundWorkOperationLifecycleService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/InboundWorkOperationLifecycleService.java) → [WorkOperationVoidService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java)/Farm 보상 port → 생성 Mutation 보상 → 입고 취소/재개와 감사 기록. 입고 row는 삭제하지 않는다.

### 7.5 작업 종료·취소·일괄 보상·보정

- [WorkOperationProgressService.endRemaining()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationProgressService.java)은 미완료 대상을 닫고 [WorkOperation.stop()](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkOperation.java)을 호출한다. 완료 효과를 보상하는 경로와 분리됨.
- [WorkOperationVoidService.cancelOperation()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java) → 작업/실행 잠금·eligibility 재검증 → 기록형 효과 취소 또는 structure/potting port `compensate()` → 미완료 대상/연관 폐기 종료 → 원본 상태 CANCELED/VOIDED.
- `cancelBatch()`는 선택 작업 ID 순 root 잠금 → 관련 effects 일괄 조회 → [FarmStructureChangeVoidAdapter.compensateBatch()](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmStructureChangeVoidAdapter.java) → Engine 일괄 보상 → Work 상태·효과 취소. 최종 생성 취소 대상도 같은 요청에서 처리할 수 있음.
- [WorkOperationCorrectionService.create()](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java) → correction receipt 원자 claim·잠금·지문 → 원본 작업 잠금 → [WorkCorrectionPort.correct()](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkCorrectionPort.java) → [FarmWorkCorrectionAdapter](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/FarmWorkCorrectionAdapter.java)가 결과 사용 검사·날짜 보정·Engine 변경 → [WorkOperationCorrection.complete()](../backend/src/main/java/com/greenhouse/backend/work/domain/correction/WorkOperationCorrection.java)과 receipt 결과 저장. 보정은 별도 WorkOperation을 생성하지 않음.

### 7.6 전표 생성·수정·완료·취소

- [SalesController](../backend/src/main/java/com/greenhouse/backend/sales/controller/SalesController.java) → [SalesSlipCreationService.create()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipCreationService.java) → Partner 활성/유형 확인 → 번호 발급 → [SalesSlipAllocationFactory.createItems()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipAllocationFactory.java)의 group 잠금·CREATION snapshot → 전표 저장 → [SalesSlipInventoryService.reserve()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java) → Engine `reserve()`와 배분별 SalesInventoryMovement.
- 생성 요청이 이미 완료 상태이면 같은 `create()`에서 [SalesSlipOutboundService.complete()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java)도 호출한다. 예약과 실제 출고가 하나의 요청 안에서 함께 발생할 수 있음.
- [SalesSlipUpdateService.update()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipUpdateService.java) → 전표 잠금·수정 검증 → 이전 예약 해제 → 품목/배분 갱신·새 snapshot → 재예약 → 관련 거래처 미수 재계산.
- [SalesSlipStatusService.updateStatus()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipStatusService.java)의 완료 경로 → [SalesSlipOutboundService.complete()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipOutboundService.java) → [OrchidGroupReader.lockStates()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java) → OUTBOUND snapshot → 경매일 때 [AuctionShipmentCreator.create()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentCreator.java) → Engine `consumeReservation()` → 재고 이동·상태 반영.
- 취소 경로는 일반/경매·작성중/완료를 구분하여 예약 해제 또는 Engine `restoreOutbound()`를 사용한다. 경매 취소는 [AuctionSalesSlipCancellationPolicy.cancelShipmentIfPossible()](../backend/src/main/java/com/greenhouse/backend/sales/application/AuctionSalesSlipCancellationPolicy.java)와 [AuctionShipmentLifecycleService.deleteDraftShipment()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionShipmentLifecycleService.java)로 결과·정산 연결 없는 출하를 제거할 수 있다. 판매 전표 자체는 취소 상태로 보존.

### 7.7 Auction 결과와 정산

- [AuctionTrackingController](../backend/src/main/java/com/greenhouse/backend/auction/controller/AuctionTrackingController.java) → [AuctionTrackingService.addResult()/confirmReturn()/adjust()/changeStatus()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java) → [AuctionShipmentLotRepository.findForUpdateById()](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionShipmentLotRepository.java) → lot domain 메서드 → Attempt/ResultLine/StatusHistory 저장.
- 결과 등록 자체가 Settlement 서비스 호출로 이어지지는 않는다. [AuctionSettlementService.rebuild()](../backend/src/main/java/com/greenhouse/backend/settlement/application/AuctionSettlementService.java) 또는 startup의 `rebuildExistingResults()`가 AuctionDataReader의 결과 값으로 정산선을 생성한다.
- `rebuildExistingResults()`는 sold result ID를 500건씩 읽고 이미 연결된 ID를 제외 → 거래처 잠금 → 연결 여부 재확인 → 정산별 새 행 snapshot 결합·예상 입금일 반영 → 저장.
- [AuctionSettlementLine](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlementLine.java)은 최초 반영 수량·단가·금액을 보관하며 상세 응답의 Auction 현재 참조 정보와 구분한다.

### 7.8 일반/경매 입금과 잔액

- 일반: [SalesPaymentService.confirmPayment()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java) → 전표 root 잠금 → 도메인 대상 검증 → 거래처 잠금 → [PaymentLedgerService.findManualPayment()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java) → 신규 입금만 전표 금액 반영 → `recordManualPayment()` → 미수 집계·잔액·대상 감사.
- 경매: [PaymentService.confirmAuctionPayment()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java) → 경매장 ID 조회 → 거래처 잠금 → 정산 root 잠금 → replay 확인 → 정산 입금 → payment event·잔액 activity·감사.
- [PaymentLedgerService.recordManualPayment()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java)는 PAYMENT_RECEIVED와 MANUAL_MATCH_CONFIRMED 이벤트를 저장한다. `externalUid`는 대상 유형·ID·키로 구성하며 replay는 금액·입금일을 검증.
- [PartnerSettlementSettingsService.getOrCreate()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerSettlementSettingsService.java)와 [PartnerBalanceService.getBalance()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PartnerBalanceService.java)는 거래처 잠금 후 기본 행을 생성할 수 있다. HTTP GET에도 write path가 존재한다.

### 7.9 기준정보·그룹·감사·원장 전환

- [VarietyService](../backend/src/main/java/com/greenhouse/backend/farm/application/variety/VarietyService.java)/[MaterialService](../backend/src/main/java/com/greenhouse/backend/farm/application/material/MaterialService.java)는 생성·수정·비활성화·삭제를 처리하고 Repository의 코드 sequence 경로를 사용한다. 품종은 입고의 신규/기존 선택도 담당.
- [OrchidGroupCollectionService](../backend/src/main/java/com/greenhouse/backend/farm/application/collection/OrchidGroupCollectionService.java)는 group 저장·보관, member 추가·removedAt 반영을 처리한다. 구조 변경 결과의 그룹 선택은 [OrchidGroupCollectionInheritanceService](../backend/src/main/java/com/greenhouse/backend/farm/application/transformation/OrchidGroupCollectionInheritanceService.java)에서 처리.
- 감사 대상 흐름은 업무별 snapshot/support → [AuditEventWriter](../backend/src/main/java/com/greenhouse/backend/audit/application/AuditEventWriter.java) → [JpaAuditRecorder.record()](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java) → AuditEventRepository. 모든 업무 API가 감사를 생성한다고 일반화하지 않음.
- 원장 전환: [OrchidGroupStateChainMigrationService.validate()/importManifest()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupStateChainMigrationService.java) → Mutation/Entry 적재·Work source/link application 호출·Farm lineage 연결·현재 revision 반영. [OrchidGroupLedgerPreparationService.prepare()/startImport()/activate()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerPreparationService.java)와 [OrchidGroupLedgerCutoverService.execute()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupLedgerCutoverService.java)가 별도 단계 경계를 구성한다.

## 8. 주요 read path

| 조회 | 실제 경로·데이터 범위 |
| --- | --- |
| 농장 구조 | [FarmStructureController](../backend/src/main/java/com/greenhouse/backend/farm/controller/structure/FarmStructureController.java)/[OrchidGroupQueryController](../backend/src/main/java/com/greenhouse/backend/farm/controller/orchid/OrchidGroupQueryController.java) → [FarmQueryService](../backend/src/main/java/com/greenhouse/backend/farm/application/structure/FarmQueryService.java) → 구조·group batch 조회 → 응답. `getOrchidGroup()`은 개별 상세 |
| 맵·viewport | [FarmStatusController](../backend/src/main/java/com/greenhouse/backend/farm/controller/status/FarmStatusController.java) → [FarmStatusService.getMap()](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmStatusService.java)의 `findMapRows()` projection; viewport는 다이 순서와 선택 범위 group batch 조립. 별도 `getOrchidManagementBedOrder()`가 전체 다이 순서 제공 |
| 입고·품종·자재 | 각각 query/service → 페이지 Repository → 결과·요약 assembler. 입고 결과 group과 potting date는 group/Mutation Entry에서 읽음; 입고 컬럼 복제값으로 취급하지 않음 |
| 사용자/자동 그룹 | Collection 목록 → member 일괄 조회 → group 상세 일괄 조회; 자동 그룹은 현재 속성·수량·상태 후보를 조회해 계산 |
| Work 목록·캘린더 | [WorkOperationQueryService.search()/getCalendar()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationQueryService.java) → Operation QueryDSL → [WorkOperationSummaryAssembler](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationSummaryAssembler.java); 대상 전체 배열 대신 execution 집계·action·correction·relation 요약 |
| Work 상세 | `get()` → operation/work type → response assembler의 targets/executions/현재 미완료 입고 값. [WorkOperationDetailService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationDetailService.java)는 effect·SOURCE/RESULT·correction·표시 참조를 별도로 일괄 조립 |
| 통합 작업 이력 | `getWorkHistory()` → 조회 범위 resolve → [WorkOperationRepository.findHistoryPage()](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationRepository.java) → 페이지 작업 ID로 target/effect 연결 조회·중복 제거. 기존 `getOrchidGroupHistory()` 전체 조회 경로도 존재 |
| 관계·그래프 | [WorkOperationRelationQueryService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationRelationQueryService.java)는 receipt membership 또는 명시적 parent relation; [WorkOperationGraphQueryService.get()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationGraphQueryService.java)는 효과·보정과 Farm Mutation fragment 조합. 같은 Receipt의 형제 작업을 실행 그래프 관계로 추정하지 않음 |
| Mutation 조회 | dev 조건부 [OrchidGroupMutationQueryController](../backend/src/main/java/com/greenhouse/backend/farm/controller/orchid/OrchidGroupMutationQueryController.java) → QueryService/GraphQueryService → Mutation/Entry/Relation/Lineage/Work metadata. 일반 현재 상태 조회와 분리 |
| 판매 페이지·상세 | [SalesQueryService.getSalesSlipPage()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesQueryService.java) → Partner 검색 ID + Sales searchPage → summary assembler. 상세는 전표 root/품목 → allocation/snapshot 일괄 → 현재 Farm 상태·Partner·Auction 이름·action 조립 |
| Auction 목록·상세 | [AuctionTrackingService.getLots()/getLot()](../backend/src/main/java/com/greenhouse/backend/auction/application/AuctionTrackingService.java) → Lot 검색/상세 → 시도·결과·상태 이력 및 Partner 현재 이름 조립. summary는 집계 조회 |
| 경매 정산·입금 원장 | `getSettlementPage()`는 정산 root + Partner 이름; `getSummary()`는 전체 조건 DB 합계; `getSettlement()`는 lines+Auction 결과 참조. [PaymentService.getEventPage()](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentService.java)는 eventType을 페이지 조회 전에 적용하고 Partner 이름을 batch 조립 |
| Dashboard | [DashboardQueryService.getSummary()](../backend/src/main/java/com/greenhouse/backend/dashboard/application/DashboardQueryService.java) → [FarmMetricsReader.getSnapshot()](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java); 마지막 두 응답 값은 코드에서 `0`, `null`로 조립됨 |
| Analytics | [AnalyticsQueryService](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java) → [SalesMetricsReader](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesMetricsReader.java)/[FarmMetricsReader](../backend/src/main/java/com/greenhouse/backend/farm/application/status/FarmMetricsReader.java)/[WorkOperationMetricsReader](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationMetricsReader.java) + Partner identities/현재 nonzero balances → 응답 assembler; 최상위 readOnly REPEATABLE_READ |
| 출력 | [PrintController](../backend/src/main/java/com/greenhouse/backend/print/controller/PrintController.java) → [PrintQueryService](../backend/src/main/java/com/greenhouse/backend/print/application/PrintQueryService.java) → Sales 문서/summary. HTML/PDF 렌더러가 아니라 출력용 조회 데이터 |

조회 상한은 API별로 다르다. Sales/Partner/Settlement/Payment 호환 목록의 500건, shipment option 200건, ID 조회 batch 500건은 코드에서 확인했다. 이 숫자를 Farm 구조·자동 그룹·사용자 그룹·Work 호환 이력 등 모든 목록에 일괄 적용하지 않는다. 정확한 query parameter와 schema는 해당 slice/Controller를 기준으로 확인한다.

## 9. Transaction boundary와 DB 보호 지점

### 실제 annotation 경계

| 경계 | 코드상 transaction 선언·참여 |
| --- | --- |
| Farm 명령·입고·그룹·기준정보 | Command/InboundRecord/InboundPotting/Collection/Variety/Material/BedPlacementProfile/Reconciliation service의 class-level `@Transactional`; 조회 메서드의 readOnly override는 개별 확인 |
| Mutation Engine | [OrchidGroupMutationEngine](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupMutationEngine.java) class-level MANDATORY. 최상위 업무 service가 연 transaction 필요; recorder는 같은 호출 경로에서 동작 |
| Work 계획·진행·실행·즉시 기록·취소·보정 | 각 public application service의 class-level `@Transactional`; port/handler·Engine·효과 저장·업무 상태 변경이 호출 transaction에 참여 |
| 접수·잠금 | [WorkCommandReceipts](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkCommandReceipts.java), [WorkOperationLockService](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationLockService.java), [BusinessPartnerLock](../backend/src/main/java/com/greenhouse/backend/partner/application/BusinessPartnerLock.java), [OrchidGroupReader.lockStates()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/OrchidGroupReader.java), [FarmWorkTargetResolver.lockAndValidateActive()](../backend/src/main/java/com/greenhouse/backend/farm/application/orchid/FarmWorkTargetResolver.java)는 MANDATORY |
| Sales 쓰기 | Creation/Update/Status/Payment service는 class-level `@Transactional`. Inventory/Outbound는 독립 annotation 없이 상위 유스케이스 호출. Engine MANDATORY가 transaction을 요구 |
| Auction | Tracking class는 readOnly, `addResult()/confirmReturn()/adjust()/changeStatus()`는 write override. ShipmentCreator와 Lifecycle의 삭제 메서드는 MANDATORY |
| 정산·잔액·설정·경매 입금 | Settlement/Balance/Settings/Payment service 기본 write transaction; pure 조회는 method readOnly. initializer 자체에서 DB 변경하지 않고 transactional Settlement service 호출 |
| Payment 원장·Audit 저장 | [PaymentLedgerService](../backend/src/main/java/com/greenhouse/backend/settlement/application/PaymentLedgerService.java) class와 [JpaAuditRecorder.record()](../backend/src/main/java/com/greenhouse/backend/audit/application/JpaAuditRecorder.java)는 MANDATORY |
| 일반 조회 | FarmQuery/FarmStatus/OrchidGroupReader/WorkQuery/Detail/Graph/Relation/SalesQuery/AuctionDataReader/BusinessPartnerReader/Dashboard/Print 등 readOnly |
| 분석 | [AnalyticsQueryService](../backend/src/main/java/com/greenhouse/backend/analytics/application/AnalyticsQueryService.java): readOnly + REPEATABLE_READ. 하위 읽기 호출을 같은 최상위 snapshot에서 조합하는 선언 |
| 전환 도구 | importer `importManifest()` write, `validate()` readOnly; Preparation `prepare()/startImport()/activate()` 각각 write, reconcile readOnly. CLI 전체를 무조건 하나의 transaction으로 간주하지 않음 |
| 인증·demo | AuthService·Security filter는 DB application transaction 경계가 아님. 서버 세션·SecurityContext·쿠키 또는 프로세스 카운터 처리 |

표는 annotation과 호출 지점을 기록한 것이다. 실제 proxy 적용, flush·commit 및 전체 원자성의 실행 증명은 후속 테스트 단계에 남긴다.

### 잠금·멱등성·제약의 위치

- Group/Partner 다중 잠금은 `findAllForUpdateByIdIn()`의 ID 정렬과 PESSIMISTIC_WRITE. 입고는 [InboundRecordRepository.findRootsForUpdateByIdIn()](../backend/src/main/java/com/greenhouse/backend/farm/repository/inbound/InboundRecordRepository.java)로 root를 먼저 잠근 뒤 association을 읽는다.
- [WorkOperationLockService.lockAll()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationLockService.java)는 연결 입고 잠금 후 operation root를 잠금. `lockInboundPlans()`는 형제 입고를 합쳐 잠근 뒤 활성 계획 ID를 재검증한다. [WorkOperationVoidService.cancelBatch()](../backend/src/main/java/com/greenhouse/backend/work/application/operation/WorkOperationVoidService.java)는 선택 operation root를 직접 잠그는 별도 경로다.
- 판매 입금은 slip → partner 순서, 경매 입금은 partner → settlement 순서가 실제 메서드에서 관찰된다. 하나의 추상 잠금 순서로 합쳐 기술하지 않는다.
- [WorkCommandReceiptRepository.claim()](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkCommandReceiptRepository.java)/[WorkCorrectionReceiptRepository.claim()](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkCorrectionReceiptRepository.java)의 INSERT ON CONFLICT와 행 잠금, 효과 key/fingerprint, [SalesSlipNumberRepository.nextDailySequence()](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipNumberRepository.java)의 원자 카운터 SQL은 서로 다른 키·목적을 소유.
- `V14__enforce_inventory_and_sales_consistency.sql`: 수량·예약 CHECK 및 version. `V15__add_auction_settlement_version.sql`, `V16__enforce_auction_and_balance_concurrency.sql`: version·경매 차수 UNIQUE 등.
- `V21__add_orchid_group_mutation_engine.sql`: Mutation source identity, group revision, Entry 종류·full snapshot·revision 제약 및 coverage. `V22__enforce_orchid_group_mutation_write_fence.sql`: ACTIVE에서 transaction-local context/revision 확인, 물리 DELETE 차단, deferred constraint trigger로 Entry 존재 확인.
- [OrchidGroupWriteFenceRepository.authorizeMutation()](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupWriteFenceRepository.java)는 PostgreSQL `set_config(..., TRUE)`로 transaction context 설정. H2에서는 이 동작을 실행하지 않는다.
- `V25__add_work_command_receipts.sql`, `V26__align_work_effect_idempotency.sql`, `V33__normalize_work_command_receipt_memberships.sql`, `V42__work_correction_audit_events.sql`이 접수·효과·membership·보정 계약을 반영.

## 10. API·인증·운영 적용 범위와 문서 대조

전체 RestController의 mapping annotation과 15개 OpenAPI slice를 HTTP method/path 기준으로 정적 대조했다. 양쪽 모두 143개 operation이며 한쪽에만 있는 method/path는 없었다. path variable 이름은 정규화해 비교했다. 이는 source mapping의 일치만 확인한 결과이며 schema·validation drift, 실제 profile별 노출, 인증·실행 성공을 보증하지 않는다. 생성 스크립트 실행으로 확인한 결과도 아니다.

| OpenAPI slice | 실제 소유/진입 모듈 |
| --- | --- |
| [auth](../docs/api/slices/auth.openapi.yaml) | auth |
| [farm-structure](../docs/api/slices/farm-structure.openapi.yaml) | farm 구조·난 묶음 조회 |
| [farm-status](../docs/api/slices/farm-status.openapi.yaml) | farm 현황, dashboard |
| [orchid-command](../docs/api/slices/orchid-command.openapi.yaml) | farm 명령·배치·분갈이 호환 진입 |
| [orchid-mutation](../docs/api/slices/orchid-mutation.openapi.yaml) | farm dev 진단 조회 |
| [inventory](../docs/api/slices/inventory.openapi.yaml) | farm 품종·자재·입고 |
| [orchid-collection](../docs/api/slices/orchid-collection.openapi.yaml) | farm 사용자 그룹 |
| [derived-orchid-group](../docs/api/slices/derived-orchid-group.openapi.yaml) | farm 자동 그룹 |
| [work](../docs/api/slices/work.openapi.yaml) | work 유형·metadata |
| [work-operation](../docs/api/slices/work-operation.openapi.yaml) | work 계획·실행·취소·보정·조회 |
| [partner](../docs/api/slices/partner.openapi.yaml) | partner, settlement 설정 |
| [sales](../docs/api/slices/sales.openapi.yaml) | sales, print |
| [auction](../docs/api/slices/auction.openapi.yaml) | auction, settlement 정산 |
| [payment](../docs/api/slices/payment.openapi.yaml) | settlement 입금·잔액·원장 |
| [analytics](../docs/api/slices/analytics.openapi.yaml) | analytics |

| 문서에서 확인한 설명 | 실제 코드로 확인한 적용 범위 |
| --- | --- |
| Mutation Engine 단일 쓰기와 전환 설명 | 실제 Command/Inbound/Sales/Farm Work handler가 Engine을 호출. `docs/04-architecture.md` 4.1에는 Legacy 라우팅 표현도 남아 있으나 현행 [SalesSlipInventoryService](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesSlipInventoryService.java)에는 Engine 분기가 구현됨. 미래 제거 목록을 현재 코드로 가정하지 않음 |
| ADR-001의 adapter 최종 배치 방향 | 실제 Farm에 [WorkEffectHandler](../backend/src/main/java/com/greenhouse/backend/work/application/effect/WorkEffectHandler.java)/Work port 구현이 존재. ADR의 최종 배치 목표와 현재 package 구조를 별도로 기록 |
| 거래처를 먼저 잠근다는 판매 정책 설명 | [SalesPaymentService.confirmPayment()](../backend/src/main/java/com/greenhouse/backend/sales/application/SalesPaymentService.java)와 Sales Update/Status에서 전표 root 잠금부터 시작하는 경로가 있음. 경매 정산 입금은 거래처부터 잠금. 정책 문구만으로 실제 순서를 확정하지 않음 |
| GET을 통한 설정·잔액 조회 | `getOrCreate()/getBalance()`는 기본 row 생성 가능한 write transaction. 일반 read path와 별도 기록 |
| Mutation slice의 조회 API | Controller에 `app.environment=dev` 조건이 있음. 기본 application 환경은 prod이므로 저장된 OpenAPI만으로 운영 노출을 단정하지 않음 |
| 일반 인증·demo | 일반 모드: 공개 auth login/me/context·health/docs, work-types ADMIN, 나머지 API 인증. demo 모드: STATELESS demo 인증·보호 filter; auth disabled 모드는 별도 permitAll 분기 |
| 세션·기본 계정 | [AuthService](../backend/src/main/java/com/greenhouse/backend/auth/AuthService.java)가 session SecurityContext 저장·logout invalidate; cookie writer/refresh filter가 갱신·만료. 기본 계정은 다른 UserDetailsService Bean이 없을 때만 자동 구성; 계정 JPA Entity는 없음 |
| A5 출력 | backend는 [SalesSlipDocument](../backend/src/main/java/com/greenhouse/backend/sales/application/document/SalesSlipDocument.java)와 페이지 데이터를 제공. A5 브라우저 layout/rendering의 구현 여부는 이 backend 조사 범위 밖 |
| 원장 전환의 운영 사실 | repository에 CLI/importer/guard/fence 구현이 있음. 실제 운영 DB의 manifest 적용·coverage 상태는 확인하지 않음 |
| 보정 모델 | 현행 [WorkOperationCorrectionService](../backend/src/main/java/com/greenhouse/backend/work/application/correction/WorkOperationCorrectionService.java)와 V42는 독립 correction WorkOperation 대신 원본 감사 row/receipt를 사용. V42는 기존 correction 자료가 있으면 예외로 중단하는 선행 조건이 있음 |
| 미구현 정책 범위 | 은행 CSV/예치금/자동 매칭·외부 경매 import는 저장된 slice 및 Controller에 공개 명령 경로가 없음. 설정 필드 존재를 연동 구현으로 해석하지 않음 |

이 대조는 설명의 적용 범위를 표시한 것이다. 기존 문서 수정이나 결함 판정은 이번 단계에서 수행하지 않는다. 전체 OpenAPI 재생성·drift 검사는 미실행이다.

현재 활성 Flyway 파일은 `db/migration/` 아래 V1~V42 범위이며 V31/V34 번호 파일은 없다. `db/migration-archive/`는 별도 보존 폴더다. 운영 DB에 어떤 파일이 실제 적용됐는지는 이 inventory로 알 수 없다.

## 11. 핵심 테스트와 검증 경로

### 실행 구분

- `backend/build.gradle.kts`의 `test`: JUnit Platform, `work-e2e`/`work-benchmark` tag 제외. H2 기반 통합과 순수 규칙·architecture 테스트 포함.
- `workE2eTest`: `work-e2e` tag만 실행. 이름과 달리 Farm/Sales/Auction/Settlement/Analytics/migration 회귀도 포함.
- `workBenchmark`: `work-benchmark` tag. `-PworkBenchmarkEnforce=true`가 query limit 강제 여부를 설정.
- `.github/workflows/verify.yml`: backend check/bootJar, Docker 기반 E2E/benchmark, API 재생성·생성물 diff 검사 구성. CI 정의 존재를 현재 브랜치 실행 성공으로 간주하지 않음.

### 구조·업무별 근거

| 대상 | 테스트 소스와 실제 검증 항목의 예 |
| --- | --- |
| 모듈·writer 경계 | [ModularArchitectureTests.modulesUseOnlyDeclaredDependencies()/repositoriesAreAccessedOnlyInsideOwningModule()](../backend/src/test/java/com/greenhouse/backend/ModularArchitectureTests.java), [ModuleBoundaryInventoryTest.compiledDependenciesFollowTheDeclaredModuleGraph()/annotatedQueriesDoNotReadForeignEntitiesOrTables()](../backend/src/test/java/com/greenhouse/backend/ModuleBoundaryInventoryTest.java), [OrchidGroupWriterArchitectureTest.runtimeHasNoLegacyRoutingClasses()/directStateMutationCallersMatchTheLifecycleInventories()](../backend/src/test/java/com/greenhouse/backend/farm/application/orchid/mutation/OrchidGroupWriterArchitectureTest.java) |
| 시간·인증·demo·공통 오류 | [ClockPersistenceIntegrationTest](../backend/src/test/java/com/greenhouse/backend/ClockPersistenceIntegrationTest.java), [TimeConfigTests](../backend/src/test/java/com/greenhouse/backend/common/config/TimeConfigTests.java), [AuthIntegrationTests](../backend/src/test/java/com/greenhouse/backend/AuthIntegrationTests.java), [CustomAccountAuthIntegrationTest](../backend/src/test/java/com/greenhouse/backend/CustomAccountAuthIntegrationTest.java), [SecurityConfigConditionTest](../backend/src/test/java/com/greenhouse/backend/auth/SecurityConfigConditionTest.java), [DemoModeIntegrationTests](../backend/src/test/java/com/greenhouse/backend/DemoModeIntegrationTests.java), Demo filter/policy/limiter tests, [ErrorResponseWriterTest](../backend/src/test/java/com/greenhouse/backend/common/api/ErrorResponseWriterTest.java), [PaginationContractIntegrationTest](../backend/src/test/java/com/greenhouse/backend/PaginationContractIntegrationTest.java) |
| Farm·입고·그룹 | [FarmStructureIntegrationTests](../backend/src/test/java/com/greenhouse/backend/FarmStructureIntegrationTests.java), [BedPlacementTests](../backend/src/test/java/com/greenhouse/backend/BedPlacementTests.java), [BedPlacementProfileTests](../backend/src/test/java/com/greenhouse/backend/BedPlacementProfileTests.java), [InventoryIntegrationTests](../backend/src/test/java/com/greenhouse/backend/InventoryIntegrationTests.java), [InboundPottingPlanIntegrationTests](../backend/src/test/java/com/greenhouse/backend/InboundPottingPlanIntegrationTests.java), [OrchidGroupCollectionIntegrationTests](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCollectionIntegrationTests.java), [DerivedOrchidGroupIntegrationTests](../backend/src/test/java/com/greenhouse/backend/DerivedOrchidGroupIntegrationTests.java), [OrchidGroupCreationCancellationIntegrationTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCreationCancellationIntegrationTest.java) |
| Engine·동시성·fence | [OrchidGroupMutationEngineIntegrationTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupMutationEngineIntegrationTest.java), [OrchidGroupMutationPostgresE2ETest.serializesConcurrentCreationIntoTheSamePlacement()/serializesConcurrentSalesReservationAndDiscardForTheSameGroup()/enforcesTheActiveLedgerWriteFenceAndRollsBackMutationContext()](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupMutationPostgresE2ETest.java) |
| 원장 import·대사 | [OrchidGroupStateChainMigrationPostgresE2ETest.importsCompleteChainsReplaysIdempotentlyAndActivatesCoverage()](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupStateChainMigrationPostgresE2ETest.java), [OrchidGroupLedgerReconciliationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/OrchidGroupLedgerReconciliationPostgresE2ETest.java), writer guard/CLI/fingerprint compatibility tests |
| Work 상태·실행 계약 | [WorkOperationIntegrationTests](../backend/src/test/java/com/greenhouse/backend/WorkOperationIntegrationTests.java), [WorkOperationScopeIntegrationTests](../backend/src/test/java/com/greenhouse/backend/WorkOperationScopeIntegrationTests.java), [RepotWorkOperationIntegrationTests](../backend/src/test/java/com/greenhouse/backend/RepotWorkOperationIntegrationTests.java), [DivideMergeWorkOperationIntegrationTests](../backend/src/test/java/com/greenhouse/backend/DivideMergeWorkOperationIntegrationTests.java), [MovementBatchWorkOperationIntegrationTests](../backend/src/test/java/com/greenhouse/backend/MovementBatchWorkOperationIntegrationTests.java), [WorkOperationContractE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkOperationContractE2ETest.java), [WorkStructureChangeE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkStructureChangeE2ETest.java) |
| Work 멱등·실패 원자성 | [WorkIdempotencyPostgresE2ETest.concurrentImmediateRequestsCreateOneOperationAndOneEffect()/aFailureInTheSecondBatchRecordRollsBackAllResultsAndTheReceipt()](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkIdempotencyPostgresE2ETest.java) |
| 취소·복구·참조 경쟁 | [WorkBatchCancellationPostgresE2ETest.concurrentRetriesCreateOnlyOneCompensationAndAuditSet()/failureAfterMutationFlushRollsBackGroupsWorkAndAudit()](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkBatchCancellationPostgresE2ETest.java), [WorkUndoSafetyPostgresE2ETest.completeTargetAndCancelSerializeWithoutDeadlock()](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkUndoSafetyPostgresE2ETest.java), UndoInbound/UndoStateProtection tests |
| 보정 감사·접수·rollback | [WorkCorrectionAuditPostgresE2ETest.concurrentDuplicateRequestsCreateOneAuditAndOneMutation()/dateOnlyCorrectionHasNoMutationAndKeyReuseIsRejected()/failedBulkCorrectionRollsBackReceiptAuditAndAllQuantityChanges()](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionAuditPostgresE2ETest.java), [WorkCorrectionMigrationPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkCorrectionMigrationPostgresE2ETest.java) |
| 판매·예약·snapshot | [SalesIntegrationTests](../backend/src/test/java/com/greenhouse/backend/SalesIntegrationTests.java), [SalesOrchidGroupSnapshotIntegrationTest](../backend/src/test/java/com/greenhouse/backend/SalesOrchidGroupSnapshotIntegrationTest.java), [SalesInventoryPostgresE2ETest.concurrentReservationsLockGroupsInIdOrderAndRejectOverbooking()/laterFailureRollsBackStockSnapshotsShipmentsAndMovements()](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesInventoryPostgresE2ETest.java), SalesInventory mutation contract tests |
| 번호·거래처·입금·정산 | [SalesSlipNumberPostgresE2ETest.allocatesUniqueDailyNumbersConcurrentlyOnPostgres()](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesSlipNumberPostgresE2ETest.java), [PartnerSettlementPostgresE2ETest.concurrentFirstReadsReturnTheSameDefaultSettings()/concurrentInitializersLinkEachResultOnlyOnce()/aLaterFailureRollsBackTheSalesPaymentAndAllLedgerEffects()](../backend/src/test/java/com/greenhouse/backend/work/e2e/PartnerSettlementPostgresE2ETest.java), [PaymentTests](../backend/src/test/java/com/greenhouse/backend/PaymentTests.java), [AuctionTrackingTests](../backend/src/test/java/com/greenhouse/backend/AuctionTrackingTests.java), [AuctionSettlementTests](../backend/src/test/java/com/greenhouse/backend/AuctionSettlementTests.java) |
| query count·규모 | [CoreQueryRegressionTest.farmViewportUsesFixedQueryCount()/salesSlipDetailLoadsAllocationsAndActionsWithFixedQueryCount()](../backend/src/test/java/com/greenhouse/backend/CoreQueryRegressionTest.java), [FarmQueryPostgresE2ETest.structureQueriesStayBoundedAndMapDoesNotLoadGroupEntities()](../backend/src/test/java/com/greenhouse/backend/work/e2e/FarmQueryPostgresE2ETest.java), [WorkDetailQueryPostgresE2ETest.correctionDetailsUseBoundedQueriesAndKeepMissingHistory()](../backend/src/test/java/com/greenhouse/backend/work/e2e/WorkDetailQueryPostgresE2ETest.java), [OrchidGroupCollectionQueryTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupCollectionQueryTest.java), WorkOperation/SearchScalability benchmark |
| 분석·출력·모듈 간 계약 | [AnalyticsMetricsPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/AnalyticsMetricsPostgresE2ETest.java), [SalesAnalyticsPostgresE2ETest.joinedMetricsUseOneSnapshotWhenAnotherTransactionChangesSalesNamesAndBalances()](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAnalyticsPostgresE2ETest.java), [SalesAuctionBoundaryPostgresE2ETest.printKeepsItsExistingDocumentAndListJson()/dashboardReadsFiveAggregatesWithoutLoadingEntities()](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAuctionBoundaryPostgresE2ETest.java), [InputChannelExtensionPostgresE2ETest](../backend/src/test/java/com/greenhouse/backend/work/e2e/InputChannelExtensionPostgresE2ETest.java) |
| 감사 | [OrchidGroupAuditRollbackIntegrationTest](../backend/src/test/java/com/greenhouse/backend/OrchidGroupAuditRollbackIntegrationTest.java), 각 기준정보·입고·판매 audit integration test, [AuditEventWriterTest](../backend/src/test/java/com/greenhouse/backend/audit/application/AuditEventWriterTest.java), [SalesAuctionBoundaryPostgresE2ETest.cliAuditRequiresCallerTransactionAndRollsBackWithIt()](../backend/src/test/java/com/greenhouse/backend/work/e2e/SalesAuctionBoundaryPostgresE2ETest.java) |

Architecture 예외 inventory는 `backend/src/test/resources/architecture/` 아래에 있다. 어떤 규칙을 검사하는지와 테스트가 실제 통과하는지는 분리해 후속 평가에서 확인한다.

## 12. Entity·Repository 파일 inventory

다음 inventory는 실제 main 소스의 선언과 파일 목록에서 추출한다. Entity는 `@Entity`/`@Table`, Repository는 repository 패키지의 Java 파일 기준이다. Custom interface/Impl, JDBC 구현도 포함하며 projection row 파일은 저장소 타입과 구분한다.

### auction

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [AuctionAttempt](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionAttempt.java) | `auction_attempts` |
| [AuctionLotStatusHistory](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionLotStatusHistory.java) | `auction_lot_status_history` |
| [AuctionResultLine](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionResultLine.java) | `auction_result_lines` |
| [AuctionShipment](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipment.java) | `auction_shipments` |
| [AuctionShipmentLot](../backend/src/main/java/com/greenhouse/backend/auction/domain/AuctionShipmentLot.java) | `auction_shipment_lots` |

| Repository 파일 | 형태 |
| --- | --- |
| [AuctionAttemptRepository](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionAttemptRepository.java) | Spring Data JPA |
| [AuctionLotStatusHistoryRepository](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionLotStatusHistoryRepository.java) | Spring Data JPA |
| [AuctionResultLineRepository](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionResultLineRepository.java) | Spring Data JPA |
| [AuctionShipmentLotRepository](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionShipmentLotRepository.java) | Spring Data JPA |
| [AuctionShipmentLotRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionShipmentLotRepositoryCustom.java) | custom 조회 interface |
| [AuctionShipmentLotRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionShipmentLotRepositoryImpl.java) | custom 구현 |
| [AuctionShipmentRepository](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionShipmentRepository.java) | Spring Data JPA |

### audit

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [AuditEventEntity](../backend/src/main/java/com/greenhouse/backend/audit/domain/AuditEventEntity.java) | `audit_events` |

| Repository 파일 | 형태 |
| --- | --- |
| [AuditEventRepository](../backend/src/main/java/com/greenhouse/backend/audit/repository/AuditEventRepository.java) | Spring Data JPA |

### farm

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [OrchidGroupCollection](../backend/src/main/java/com/greenhouse/backend/farm/domain/collection/OrchidGroupCollection.java) | `orchid_group_collections` |
| [OrchidGroupCollectionMember](../backend/src/main/java/com/greenhouse/backend/farm/domain/collection/OrchidGroupCollectionMember.java) | `orchid_group_collection_members` |
| [InboundRecord](../backend/src/main/java/com/greenhouse/backend/farm/domain/inbound/InboundRecord.java) | `inbound_records` |
| [Material](../backend/src/main/java/com/greenhouse/backend/farm/domain/material/Material.java) | `materials` |
| [OrchidGroupLedgerCoverage](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupLedgerCoverage.java) | `orchid_group_ledger_coverages` |
| [OrchidGroupMutation](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutation.java) | `orchid_group_mutations` |
| [OrchidGroupMutationEntry](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutationEntry.java) | `orchid_group_mutation_entries` |
| [OrchidGroupMutationRelation](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/mutation/OrchidGroupMutationRelation.java) | `orchid_group_mutation_relations` |
| [OrchidGroup](../backend/src/main/java/com/greenhouse/backend/farm/domain/orchid/OrchidGroup.java) | `orchid_groups` |
| [BedZone](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/BedZone.java) | `bed_zones` |
| [BedZoneCapacity](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/BedZoneCapacity.java) | `bed_zone_capacities` |
| [House](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/House.java) | `houses` |
| [PhysicalBed](../backend/src/main/java/com/greenhouse/backend/farm/domain/structure/PhysicalBed.java) | `physical_beds` |
| [OrchidGroupLineage](../backend/src/main/java/com/greenhouse/backend/farm/domain/transformation/OrchidGroupLineage.java) | `orchid_group_lineage` |
| [Variety](../backend/src/main/java/com/greenhouse/backend/farm/domain/variety/Variety.java) | `varieties` |

| Repository 파일 | 형태 |
| --- | --- |
| [OrchidGroupCollectionMemberRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/collection/OrchidGroupCollectionMemberRepository.java) | Spring Data JPA |
| [OrchidGroupCollectionRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/collection/OrchidGroupCollectionRepository.java) | Spring Data JPA |
| [InboundRecordRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/inbound/InboundRecordRepository.java) | Spring Data JPA |
| [MaterialRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/material/MaterialRepository.java) | Spring Data JPA |
| [MaterialRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/farm/repository/material/MaterialRepositoryCustom.java) | custom 조회 interface |
| [MaterialRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/farm/repository/material/MaterialRepositoryImpl.java) | custom 구현 |
| [OrchidGroupLedgerCoverageRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupLedgerCoverageRepository.java) | Spring Data JPA |
| [OrchidGroupMutationEntryRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupMutationEntryRepository.java) | Spring Data JPA |
| [OrchidGroupMutationRelationRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupMutationRelationRepository.java) | Spring Data JPA |
| [OrchidGroupMutationRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupMutationRepository.java) | Spring Data JPA |
| [OrchidGroupWriteFenceRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/mutation/OrchidGroupWriteFenceRepository.java) | JDBC 구현 |
| [OrchidGroupRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupRepository.java) | Spring Data JPA |
| [BedZoneRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneRepository.java) | Spring Data JPA |
| [HouseRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/HouseRepository.java) | Spring Data JPA |
| [PhysicalBedRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/PhysicalBedRepository.java) | Spring Data JPA |
| [OrchidGroupLineageRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/transformation/OrchidGroupLineageRepository.java) | Spring Data JPA |
| [VarietyRepository](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyRepository.java) | Spring Data JPA |
| [VarietyRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyRepositoryCustom.java) | custom 조회 interface |
| [VarietyRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyRepositoryImpl.java) | custom 구현 |

### partner

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [BusinessPartner](../backend/src/main/java/com/greenhouse/backend/partner/domain/BusinessPartner.java) | `business_partners` |

| Repository 파일 | 형태 |
| --- | --- |
| [BusinessPartnerRepository](../backend/src/main/java/com/greenhouse/backend/partner/repository/BusinessPartnerRepository.java) | Spring Data JPA |
| [BusinessPartnerRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/partner/repository/BusinessPartnerRepositoryCustom.java) | custom 조회 interface |
| [BusinessPartnerRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/partner/repository/BusinessPartnerRepositoryImpl.java) | custom 구현 |

### sales

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [SalesInventoryMovement](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesInventoryMovement.java) | `sales_inventory_movements` |
| [SalesOrchidGroupSnapshot](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesOrchidGroupSnapshot.java) | `sales_orchid_group_snapshots` |
| [SalesSlip](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlip.java) | `sales_slips` |
| [SalesSlipDailySequence](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipDailySequence.java) | `sales_slip_daily_sequences` |
| [SalesSlipItem](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItem.java) | `sales_slip_items` |
| [SalesSlipItemAllocation](../backend/src/main/java/com/greenhouse/backend/sales/domain/SalesSlipItemAllocation.java) | `sales_slip_item_allocations` |

| Repository 파일 | 형태 |
| --- | --- |
| [SalesInventoryMovementRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesInventoryMovementRepository.java) | Spring Data JPA |
| [SalesSlipItemAllocationRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipItemAllocationRepository.java) | Spring Data JPA |
| [SalesSlipNumberRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipNumberRepository.java) | JDBC 구현 |
| [SalesSlipRepository](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepository.java) | Spring Data JPA |
| [SalesSlipRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepositoryCustom.java) | custom 조회 interface |
| [SalesSlipRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesSlipRepositoryImpl.java) | custom 구현 |

### settlement

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [AuctionSettlement](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlement.java) | `auction_settlements` |
| [AuctionSettlementLine](../backend/src/main/java/com/greenhouse/backend/settlement/domain/AuctionSettlementLine.java) | `auction_settlement_lines` |
| [PartnerBalanceSummary](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerBalanceSummary.java) | `partner_balance_summaries` |
| [PartnerPaymentEvent](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerPaymentEvent.java) | `partner_payment_events` |
| [PartnerSettlementSettings](../backend/src/main/java/com/greenhouse/backend/settlement/domain/PartnerSettlementSettings.java) | `partner_settlement_settings` |

| Repository 파일 | 형태 |
| --- | --- |
| [AuctionSettlementRepository](../backend/src/main/java/com/greenhouse/backend/settlement/repository/AuctionSettlementRepository.java) | Spring Data JPA |
| [PartnerBalanceSummaryRepository](../backend/src/main/java/com/greenhouse/backend/settlement/repository/PartnerBalanceSummaryRepository.java) | Spring Data JPA |
| [PartnerPaymentEventRepository](../backend/src/main/java/com/greenhouse/backend/settlement/repository/PartnerPaymentEventRepository.java) | Spring Data JPA |
| [PartnerSettlementSettingsRepository](../backend/src/main/java/com/greenhouse/backend/settlement/repository/PartnerSettlementSettingsRepository.java) | Spring Data JPA |

### work

| Entity 파일 | 매핑 테이블 |
| --- | --- |
| [WorkCorrectionReceipt](../backend/src/main/java/com/greenhouse/backend/work/domain/correction/WorkCorrectionReceipt.java) | `work_correction_receipts` |
| [WorkOperationCorrection](../backend/src/main/java/com/greenhouse/backend/work/domain/correction/WorkOperationCorrection.java) | `work_operation_corrections` |
| [WorkAppliedEffect](../backend/src/main/java/com/greenhouse/backend/work/domain/effect/WorkAppliedEffect.java) | `work_applied_effects` |
| [WorkEffectOrchidGroup](../backend/src/main/java/com/greenhouse/backend/work/domain/effect/WorkEffectOrchidGroup.java) | `work_effect_orchid_groups` |
| [WorkCommandReceipt](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkCommandReceipt.java) | `work_command_receipts` |
| [WorkCommandReceiptMembership](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkCommandReceiptMembership.java) | `work_command_receipt_memberships` |
| [WorkOperation](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkOperation.java) | `work_operations` |
| [WorkType](../backend/src/main/java/com/greenhouse/backend/work/domain/operation/WorkType.java) | `work_types` |
| [WorkOperationTarget](../backend/src/main/java/com/greenhouse/backend/work/domain/target/WorkOperationTarget.java) | `work_operation_targets` |
| [WorkTargetExecution](../backend/src/main/java/com/greenhouse/backend/work/domain/target/WorkTargetExecution.java) | `work_target_executions` |

| Repository 파일 | 형태 |
| --- | --- |
| [WorkAppliedEffectRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkAppliedEffectRepository.java) | Spring Data JPA |
| [WorkCommandReceiptMembershipRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkCommandReceiptMembershipRepository.java) | Spring Data JPA |
| [WorkCommandReceiptRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkCommandReceiptRepository.java) | Spring Data JPA |
| [WorkCorrectionReceiptRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkCorrectionReceiptRepository.java) | Spring Data JPA |
| [WorkEffectOrchidGroupRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkEffectOrchidGroupRepository.java) | Spring Data JPA |
| [WorkOperationCorrectionRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationCorrectionRepository.java) | Spring Data JPA |
| [WorkOperationRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationRepository.java) | Spring Data JPA |
| [WorkOperationRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationRepositoryCustom.java) | custom 조회 interface |
| [WorkOperationRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationRepositoryImpl.java) | custom 구현 |
| [WorkOperationTargetRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationTargetRepository.java) | Spring Data JPA |
| [WorkTargetExecutionRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkTargetExecutionRepository.java) | Spring Data JPA |
| [WorkTargetExecutionRepositoryCustom](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkTargetExecutionRepositoryCustom.java) | custom 조회 interface |
| [WorkTargetExecutionRepositoryImpl](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkTargetExecutionRepositoryImpl.java) | custom 구현 |
| [WorkTypeRepository](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkTypeRepository.java) | Spring Data JPA |

Repository 패키지의 projection/row 파일(저장소 구현과 구분):

- [AuctionTrackingSummaryProjection](../backend/src/main/java/com/greenhouse/backend/auction/repository/AuctionTrackingSummaryProjection.java)
- [OrchidGroupNameRow](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupNameRow.java)
- [OrchidGroupZoneMaxSortOrderRow](../backend/src/main/java/com/greenhouse/backend/farm/repository/orchid/OrchidGroupZoneMaxSortOrderRow.java)
- [BedZoneLocationRow](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/BedZoneLocationRow.java)
- [PhysicalBedOrderRow](../backend/src/main/java/com/greenhouse/backend/farm/repository/structure/PhysicalBedOrderRow.java)
- [VarietyNameProjection](../backend/src/main/java/com/greenhouse/backend/farm/repository/variety/VarietyNameProjection.java)
- [SalesReservationReconciliationRow](../backend/src/main/java/com/greenhouse/backend/sales/repository/SalesReservationReconciliationRow.java)
- [WorkExecutionReconciliationRow](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkExecutionReconciliationRow.java)
- [WorkOperationProgressProjection](../backend/src/main/java/com/greenhouse/backend/work/repository/WorkOperationProgressProjection.java)

Entity·Repository를 직접 소유하지 않는 Auth/Demo/Common/Dashboard/Analytics/Print의 책임과 실행·조회 계약은 위 모듈 표에 수록했다.

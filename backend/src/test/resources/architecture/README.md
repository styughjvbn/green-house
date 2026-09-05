# 모듈 경계 이식 목록

`ModuleBoundaryInventoryTest`가 실제 컴파일 결과를 검사한다.
현재 예외는 리팩터링 전 `3aa8fc54`에서 확인한 호출자와 대상의 정확한 쌍이다.
검사가 통과한다는 것은 기존 결합이 해결됐다는 뜻이 아니라 새로운 우회가 추가되지 않았다는 뜻이다.

- `module-implementation-dependencies.tsv`: Entity, Q 타입, Repository, HTTP DTO, Controller 의존.
- `module-query-dependencies.tsv`: `@Query` 본문과 count query에 등장하는 다른 모듈 Entity/table 참조.
- `uncontrolled-time-calls.tsv`: Clock 없이 현재 시간을 읽는 기존 호출자. 업무 날짜 기본값은 주입된 Clock과 TimeConfig로 이전하며 예외를 축소한다.
- 실행 결과는 `build/reports/architecture/`에도 기록한다. 기준 목록을 자동 갱신하지 않는다.
- 호출부가 공개 값 계약으로 이식되면 해당 예외를 삭제한다. 제거된 예외가 남아 있어도 검사에 실패한다.
- 선언된 모듈 의존 방향은 기존 `ModularArchitectureTests`와 공유하며 완전한 클래스명 참조와 generic 의존도 검사한다.

Entity/API 이식 담당은 `docs/features/backend-refactoring-plan.md`의 B-04~14를 따른다.
Farm–Work는 B-08~10, Sales–Partner/Farm/Auction/Settlement는 B-04~07,
Analytics의 Q 타입은 B-11, Print의 HTTP DTO는 B-14에서 제거한다.

이 검사는 SQL parser가 아니다. `@Query`의 명시적 root Entity/table을 검사하며,
별칭을 통한 association join, 동적으로 조립한 Native SQL, 외부 XML query는 별도 코드 검토가 필요하다.
QueryDSL 접근은 컴파일된 Q 타입 의존 검사로 보완한다. 전체 예외를 일괄 승인하는 옵션은 두지 않는다.

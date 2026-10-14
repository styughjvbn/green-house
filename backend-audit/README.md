# Backend 감사 문서

현재 작업은 아래 세 문서에서 확인한다. 구현 정책은 프로젝트의 `docs/`·실제 코드·OpenAPI를 기준으로 한다.

| 문서 | 역할 |
| --- | --- |
| [현재 작업 목록](10-remediation-progress.md) | 다음 작업, 미완료 구현, 운영 검증, 보류 결정의 단일 목록 |
| [조회 경계 조사](15-read-boundary-review.md) | 아직 구현하지 않은 목록·하위 이력 개선의 근거와 완료 기준 |
| [측정 실행 가이드](12-domain-performance-measurement.md) | 필요할 때 실행하는 현재 benchmark·진단 도구 사용법 |

## 보관 기록

`archive/`는 과거 평가와 완료된 변경·검증 증적이다. 당시의 “문제/남은 작업”을 현재 미완료로 읽지 않는다. **보관은 모든 운영 검증이나 보류 정책이 완료됐다는 뜻이 아니다.** 미해결 범위는 현재 작업 목록에 유지한다.

| 보관 문서 | 내용 |
| --- | --- |
| `archive/00~09` | 초기 조사·감사·통합 findings·최종 평가. [원래 findings](archive/08-findings.md), [당시 최종 평가](archive/09-final-assessment.md) |
| [개선 이력](archive/10-remediation-progress.md) | 1~66차 구현·조사·커밋·검증 전체 기록 |
| [index 검증](archive/11-index-plan-validation.md) | 적용된 index/조회 변경의 PostgreSQL 계획 비교 |
| [측정 결과](archive/13-domain-performance-results.md)·[원인 조사](archive/14-performance-diagnosis.md) | 완료된 standard 전후 비교와 격리 진단 |
| [measurements](archive/measurements/) | 성능 측정과 운영 백업 대사·제약 검증의 CSV·JSON 증적 |

## 유지 기준

- 매 작업마다 새 번호 문서를 만들지 않고 관련 현재 문서를 갱신한다.
- 현재 작업 목록에는 다음 작업·미완료·보류만 유지한다. 긴 실행 과정과 반복 검증 로그를 누적하지 않는다.
- 구현 완료된 정책은 `docs/`에 반영하고, 더 이상 작업 기준이 아닌 상세 조사 기록은 `archive/`로 이동한다.
- 보관 문서는 당시 결론을 유지한다. 이동 시 상대 링크와 증적 경로를 함께 갱신한다.

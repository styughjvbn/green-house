# Seed tools

이 도구는 농장 관리 DB의 초기 데이터를 SQL로 생성합니다.

## 원칙

- GitHub/Flyway migration에는 민감하지 않은 기본 데이터만 둡니다.
- 실제 경매/일반 판매에서 파생된 데이터는 private SQL로 생성하고 Git에 올리지 않습니다.
- private SQL은 Flyway 버전 파일(`V3__...`)로 만들지 않습니다. 그래야 이후 구조 마이그레이션 `V3__add_xxx.sql`과 충돌하지 않습니다.

## 권장 구조

```text
backend/src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__seed_base_master_data.sql

scripts/seed/
  generate_all_seeds.py
  generate_base_master_seed.py
  generate_varieties_seed.py
  generate_business_partners_seed.py
  generate_auction_seed.py
  generate_direct_sales_seed.py
  auction_domain.py
  seed_common.py
  input/              # .gitignore
  generated-private/  # .gitignore
```

## 생성 명령

프로젝트 루트에서 실행합니다.

```bash
python scripts/seed/generate_all_seeds.py \
  --auction-csv "scripts/seed/input/2026 내수 출하 완 - 추출용.csv" \
  --direct-csv "scripts/seed/input/2026 일반 판매.csv" \
  --migration-output-dir "backend/src/main/resources/db/migration" \
  --private-output-dir "scripts/seed/generated-private"
```

생성 결과:

```text
backend/src/main/resources/db/migration/
  V2__seed_base_master_data.sql

scripts/seed/generated-private/
  seed_varieties_from_sales_sources.sql
  seed_business_partners.sql
  seed_auction_2025_2026.sql
  seed_direct_sales_2026.sql
  varieties_seed_review_required.csv
  auction_seed_review_required.csv
  direct_sales_seed_review_required.csv
  apply_private_seeds.ps1
  apply_private_seeds.sh
```

## DB 적용 순서

1. Docker DB 초기화
2. 백엔드 실행
3. Flyway가 `V1`, `V2`만 적용
4. private seed SQL은 `psql`로 별도 적용

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\seed\generated-private\apply_private_seeds.ps1
```

Bash:

```bash
bash scripts/seed/generated-private/apply_private_seeds.sh
```

DB 유저/DB명이 `greenhouse`가 아니면 환경변수로 지정합니다.

```bash
GREENHOUSE_DB_USER=postgres GREENHOUSE_DB_NAME=postgres bash scripts/seed/generated-private/apply_private_seeds.sh
```

PowerShell:

```powershell
$env:GREENHOUSE_DB_USER="postgres"
$env:GREENHOUSE_DB_NAME="postgres"
powershell -ExecutionPolicy Bypass -File .\scripts\seed\generated-private\apply_private_seeds.ps1
```

## 개발 중 private seed 재적용

같은 DB에 private seed를 다시 넣어야 하면 `--dev-reset`을 사용합니다.

```bash
python scripts/seed/generate_all_seeds.py \
  --auction-csv "scripts/seed/input/2026 내수 출하 완 - 추출용.csv" \
  --direct-csv "scripts/seed/input/2026 일반 판매.csv" \
  --migration-output-dir "backend/src/main/resources/db/migration" \
  --private-output-dir "scripts/seed/generated-private" \
  --dev-reset
```

주의:

- 경매 private seed는 `TRUNCATE`를 포함합니다.
- 일반 판매 private seed는 같은 slip prefix 기준 기존 데이터를 삭제합니다.
- 운영 DB에서는 사용하지 마세요.

## .gitignore 권장

```gitignore
# private seed input data
scripts/seed/input/*.csv
scripts/seed/input/*.xlsx
scripts/seed/input/*.zip

# generated private seed data
scripts/seed/generated-private/

# generated review data
*review_required*.csv

# real sales / auction data
*일반 판매*.csv
*내수 출하*.csv
*auction_seed*.sql
*direct_sales_seed*.sql
```

## 각 생성기 역할

| 파일 | 역할 |
|---|---|
| `generate_base_master_seed.py` | `work_types`, 15개 동, 45개 다이, 90개 좌/우 구역 생성 |
| `generate_varieties_seed.py` | 경매/일반 판매 소스에서 `varieties` 후보 생성 |
| `generate_business_partners_seed.py` | 경매장/일반 판매 거래처 생성 |
| `generate_auction_seed.py` | 경매 출하, lot, 경매 시도, 결과 라인, 반환 상태 이력 생성 |
| `generate_direct_sales_seed.py` | 일반 판매 전표와 품목 생성 |
| `generate_all_seeds.py` | 전체 생성 실행 |

## GitHub에 올릴 것 / 올리지 않을 것

올릴 것:

```text
V1__initial_schema.sql
V2__seed_base_master_data.sql
seed 생성 코드
README.md
```

올리지 않을 것:

```text
실제 CSV 원본
실제 거래처/품종/경매/일반 판매 seed SQL
review_required.csv
scripts/seed/generated-private/
```

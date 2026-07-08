# Seed tools

이 도구는 농장 관리 DB의 초기 데이터를 SQL로 생성합니다.

## 원칙

- GitHub/Flyway migration에는 민감하지 않은 기본 데이터만 둡니다.
- 실제 경매/일반 판매에서 파생된 데이터는 private SQL로 생성하고 Git에 올리지 않습니다.
- private SQL은 Flyway 버전 파일(`V3__...`)로 만들지 않습니다. 그래야 이후 구조 마이그레이션 `V3__add_xxx.sql`과 충돌하지 않습니다.
- 품종은 CSV에서 바로 SQL을 만들지 않고, 먼저 `varieties.json`으로 추출한 뒤 사람이 수정할 수 있게 합니다.

## 권장 구조

```text
backend/src/main/resources/db/migration/
  V1__initial_schema.sql
  V2__seed_base_master_data.sql

scripts/seed_tools/
  generate_all_seeds.py
  generate_base_master_seed.py
  generate_varieties_json.py
  generate_varieties_seed.py
  generate_business_partners_seed.py
  generate_auction_seed.py
  generate_direct_sales_seed.py
  generate_orchid_groups_seed_from_db.py
  auction_domain.py
  seed_common.py
  raw/              # .gitignore
  generated-private/  # .gitignore
```

## 생성 명령

프로젝트 루트에서 실행합니다.

```powershell
python .\scripts\seed_tools\generate_all_seeds.py `
  --auction-csv ".\scripts\seed_tools\raw\2025-2026-경매출하.csv" `
  --direct-csv ".\scripts\seed_tools\raw\2024-2026-일반판매.csv" `
  --migration-output-dir ".\backend\src\main\resources\db\migration" `
  --private-output-dir ".\scripts\seed_tools\generated-private"
```

```bash
python scripts/seed_tools/generate_all_seeds.py \
  --auction-csv "scripts/seed_tools/raw/2025-2026-경매출하.csv" \
  --direct-csv "scripts/seed_tools/raw/2024-2026-일반판매.csv" \
  --migration-output-dir "backend/src/main/resources/db/migration" \
  --private-output-dir "scripts/seed_tools/generated-private"
```

생성 결과:

```text
backend/src/main/resources/db/migration/
  V2__seed_base_master_data.sql

scripts/seed_tools/generated-private/
  varieties.json
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

## 품종 JSON 편집 흐름

첫 실행 시 `generated-private/varieties.json`이 생성됩니다.

```text
전표 CSV
→ varieties.json 추출
→ 사람이 품종명/속/별칭/기본 화분/메모 수정
→ seed_varieties_from_sales_sources.sql 생성
```

실제 난 묶음 입력 중 새 품종이 생기면 `varieties.json`의 `varieties` 배열에 직접 추가합니다.

예시:

```json
{
  "code": "VAR-MANUAL-PINK001",
  "genus": "카틀레야",
  "name": "핑크 샘플",
  "alias": null,
  "defaultPotSize": "3.5치",
  "description": "난 묶음 입력 중 수동 추가",
  "saleEnabled": true,
  "active": true,
  "memo": null,
  "reviewRequired": false,
  "reviewNote": null,
  "source": {
    "labels": ["MANUAL"],
    "auctionCount": 0,
    "directCount": 0,
    "firstSeen": null,
    "lastSeen": null
  }
}
```

주의:

- `code`는 고유해야 합니다.
- `genus + name` 조합도 중복되면 안 됩니다.
- `varieties.json`을 수동 수정한 뒤에는 `generate_all_seeds.py`를 다시 실행하면 SQL이 재생성됩니다.
- 기본 실행은 기존 `varieties.json`을 덮어쓰지 않습니다.
- CSV에서 다시 추출해 JSON을 덮어쓰고 싶을 때만 `--refresh-varieties-json`을 사용합니다.

```bash
python scripts/seed_tools/generate_all_seeds.py \
  --auction-csv "scripts/seed_tools/raw/2025-2026-경매출하.csv" \
  --direct-csv "scripts/seed_tools/raw/2024-2026-일반판매.csv" \
  --private-output-dir "scripts/seed_tools/generated-private" \
  --refresh-varieties-json
```

품종 JSON만 따로 추출할 수도 있습니다.

```bash
python scripts/seed_tools/generate_varieties_json.py \
  --auction-csv "scripts/seed_tools/raw/2025-2026-경매출하.csv" \
  --direct-csv "scripts/seed_tools/raw/2024-2026-일반판매.csv" \
  --output "scripts/seed_tools/generated-private/varieties.json" \
  --review "scripts/seed_tools/generated-private/varieties_seed_review_required.csv"
```

품종 JSON에서 SQL만 다시 만들 수도 있습니다.

```bash
python scripts/seed_tools/generate_varieties_seed.py \
  --varieties-json "scripts/seed_tools/generated-private/varieties.json" \
  --output "scripts/seed_tools/generated-private/seed_varieties_from_sales_sources.sql" \
  --review "scripts/seed_tools/generated-private/varieties_seed_review_required.csv"
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
bash scripts/seed_tools/generated-private/apply_private_seeds.sh
```

DB 유저/DB명이 `greenhouse`가 아니면 환경변수로 지정합니다.

```bash
GREENHOUSE_DB_USER=postgres GREENHOUSE_DB_NAME=postgres bash scripts/seed_tools/generated-private/apply_private_seeds.sh
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
python scripts/seed_tools/generate_all_seeds.py \
  --auction-csv "scripts/seed_tools/raw/2025-2026-경매출하.csv" \
  --direct-csv "scripts/seed_tools/raw/2024-2026-일반판매.csv" \
  --migration-output-dir "backend/src/main/resources/db/migration" \
  --private-output-dir "scripts/seed_tools/generated-private" \
  --dev-reset
```

주의:

- 경매 private seed는 `TRUNCATE`를 포함합니다.
- 일반 판매 private seed는 같은 slip prefix 기준 기존 데이터를 삭제합니다.
- 운영 DB에서는 사용하지 마세요.

## .gitignore 권장

폴더 단위로 막는 것을 권장합니다.

```gitignore
# private seed input data
scripts/seed_tools/raw/

# generated private seed data
scripts/seed_tools/generated-private/
```

## 각 생성기 역할

| 파일 | 역할 |
|---|---|
| `generate_base_master_seed.py` | `work_types`, 15개 동, 45개 다이, 90개 좌/우 구역 생성 |
| `generate_varieties_json.py` | 경매/일반 판매 소스에서 수동 편집 가능한 `varieties.json` 추출 |
| `generate_varieties_seed.py` | `varieties.json`에서 `varieties` seed SQL 생성 |
| `generate_business_partners_seed.py` | 경매장/일반 판매 거래처 생성 |
| `generate_auction_seed.py` | 경매 출하, lot, 경매 시도, 결과 라인, 반환 상태 이력 생성 |
| `generate_direct_sales_seed.py` | 일반 판매 전표와 품목 생성 |
| `generate_orchid_groups_seed_from_db.py` | 현재 DB의 난 묶음을 private seed SQL로 추출 |
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
varieties.json
review_required.csv
scripts/seed_tools/generated-private/
```

## 난 묶음 seed 추출

난 묶음은 화면에서 실제 위치와 수량을 입력한 뒤, 현재 DB 상태에서 private seed SQL로 추출합니다.
이 seed는 실제 운영 데이터이므로 Git에 올리지 않습니다.

```bash
python scripts/seed_tools/generate_orchid_groups_seed_from_db.py \
  --output scripts/seed_tools/generated-private/seed_orchid_groups.sql \
  --review scripts/seed_tools/generated-private/orchid_groups_seed_review_required.csv
```

DB 유저/DB명이 기본값(`greenhouse`)과 다르면 환경변수로 지정합니다.

```bash
GREENHOUSE_DB_USER=postgres GREENHOUSE_DB_NAME=postgres \
python scripts/seed_tools/generate_orchid_groups_seed_from_db.py
```

PowerShell:

```powershell
$env:GREENHOUSE_DB_USER="postgres"
$env:GREENHOUSE_DB_NAME="postgres"
python .\scripts\seed\generate_orchid_groups_seed_from_db.py
```

생성된 `seed_orchid_groups.sql`은 `apply_private_seeds.ps1` / `apply_private_seeds.sh`가 마지막에 자동 적용합니다. 파일이 없으면 건너뜁니다.

주의:

- `orchid_groups.id`는 덤프하지 않습니다.
- 품종은 `varieties.code`, 없으면 `genus + name`으로 다시 찾습니다.
- 위치는 `house number + physical bed number + bed zone side`로 다시 찾습니다.
- `inbound_record_id`는 복원하지 않습니다. 입고 이력까지 seed로 보존하려면 별도 추출기가 필요합니다.
- 같은 DB에 재적용할 때만 `--delete-existing`을 사용합니다.

```bash
python scripts/seed_tools/generate_orchid_groups_seed_from_db.py --delete-existing
```

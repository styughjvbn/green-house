# 데이터베이스 설계 문서 v2

## 1. 설계 원칙

- 운영 데이터는 장기간 보존한다.
- 초기에는 난 묶음 단위 관리로 시작한다.
- 난 묶음은 물리 배드가 아니라 논리 구역에 배치한다.
- 작업 이력은 최근 작업일 계산의 원천 데이터로 사용한다.
- 난 묶음 이동은 작업 이력으로 남긴다.
- 판매 전표 총액은 판매 품목 합계를 기준으로 계산한다.
- MVP 이후 판매 품목과 난 묶음 수량 연동을 고려한다.
- DB는 PostgreSQL을 기본으로 한다.

---

## 2. 주요 테이블

### houses

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 동 ID |
| number | INTEGER UNIQUE | 동 번호 |
| name | VARCHAR | 동 이름 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

### physical_beds

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 물리 배드 ID |
| house_id | BIGINT FK | 동 ID |
| number | INTEGER | 배드 번호 |
| display_order | INTEGER | 동 내부 표시 순서. 실제 농장 기준 좌→우 순서 |
| length_cm | INTEGER NULL | 대략적인 길이 |
| width_cm | INTEGER NULL | 대략적인 폭 |
| wire_count | INTEGER NULL | 철사 줄 수 |
| support_interval_cm | INTEGER NULL | 받침대 간격 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

제약 조건:

```sql
UNIQUE (house_id, number)
```

### bed_zones

논리 구역 테이블이다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 논리 구역 ID |
| physical_bed_id | BIGINT FK | 물리 배드 ID |
| name | VARCHAR | 구역명 |
| side | VARCHAR | LEFT / RIGHT / CUSTOM / HANGING |
| zone_type | VARCHAR | DEFAULT / CUSTOM / HANGING / TRAY / GRID |
| sort_order | INTEGER | 표시 순서 |
| is_active | BOOLEAN | 활성 여부 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

MVP 기본값:

```text
side: LEFT 또는 RIGHT
zone_type: DEFAULT
is_active: true
```

MVP 이후 확장 필드 후보:

| 컬럼 | 타입 | 설명 |
|---|---|---|
| grid_row | INTEGER NULL | 그리드 행 |
| grid_col | INTEGER NULL | 그리드 열 |
| x | NUMERIC NULL | 지도상 x 좌표 |
| y | NUMERIC NULL | 지도상 y 좌표 |
| width | NUMERIC NULL | 지도상 폭 |
| height | NUMERIC NULL | 지도상 높이 |

MVP에서는 좌표 필드를 필수로 사용하지 않는다.

### orchid_groups

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 난 묶음 ID |
| bed_zone_id | BIGINT FK | 논리 구역 ID |
| genus | VARCHAR | 속 |
| variety_name | VARCHAR | 품종명 |
| quantity | INTEGER | 수량 |
| pot_size | VARCHAR | 화분 크기 |
| age_year | INTEGER | 연차 |
| status | VARCHAR | 상태 |
| placement_type | VARCHAR NULL | 배치 형태: SINGLE / TRAY_20 / TRAY_15 / HANGING |
| tray_count | INTEGER NULL | 판 개수 |
| sort_order | INTEGER | 구역 내 표시 순서 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

MVP에서는 `placement_type`, `tray_count`를 선택 필드로 둔다.

### work_types

작업 유형은 기본 제공 유형과 커스텀 유형을 함께 관리하기 위해 테이블로 둔다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 작업 유형 ID |
| code | VARCHAR UNIQUE | 작업 유형 코드 |
| name | VARCHAR | 작업 유형명 |
| template | VARCHAR | 입력/표시 템플릿 |
| is_default | BOOLEAN | 기본 제공 여부 |
| is_system | BOOLEAN | 시스템 생성 전용 여부 |
| is_active | BOOLEAN | 활성 여부 |
| sort_order | INTEGER | 표시 순서 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

기본 작업 유형:

```text
농약
비료
분갈이
상태 기록
일반 메모
잎 정리
잡초 정리
단화 / 꽃 정리
위치 이동
```

### work_records

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 작업 이력 ID |
| work_type_id | BIGINT NULL FK | 작업 유형 ID |
| work_type | VARCHAR | 작업 유형명 스냅샷 |
| work_date | DATE | 작업일 |
| target_type | VARCHAR | 대상 유형 |
| target_id | BIGINT NULL | 대상 ID |
| material_name | VARCHAR | 약제명 또는 비료명 |
| dilution_ratio | VARCHAR | 희석 배수 또는 농도 |
| quantity | VARCHAR | 수량 또는 보조 정보 |
| worker | VARCHAR | 작업자 |
| from_bed_zone_id | BIGINT NULL | 위치 이동 이전 구역 |
| to_bed_zone_id | BIGINT NULL | 위치 이동 이후 구역 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

`target_type` 값:

```text
FARM
HOUSE
PHYSICAL_BED
BED_ZONE
ORCHID_GROUP
```

### customers

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 거래처 ID |
| name | VARCHAR | 거래처명 |
| owner_name | VARCHAR | 대표자명 |
| phone | VARCHAR | 연락처 |
| address | TEXT | 주소 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

### sales_slips

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 판매 전표 ID |
| slip_number | VARCHAR UNIQUE | 전표번호 |
| sale_date | DATE | 판매일 |
| customer_id | BIGINT FK | 거래처 ID |
| total_amount | INTEGER | 총 금액 |
| payment_status | VARCHAR | 입금 상태 |
| sales_status | VARCHAR | 판매 상태 |
| payment_method | VARCHAR | 결제 방식 |
| memo | TEXT | 메모 |
| created_at | TIMESTAMP | 생성일 |
| updated_at | TIMESTAMP | 수정일 |

`sales_status` 예시:

```text
작성중
판매 확정
출고 완료
취소
```

### sales_slip_items

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 판매 품목 ID |
| sales_slip_id | BIGINT FK | 판매 전표 ID |
| orchid_group_id | BIGINT NULL FK | 연결된 난 묶음 ID |
| item_name | VARCHAR | 품종명 또는 상품명 |
| genus | VARCHAR | 속 |
| spec | VARCHAR | 규격 |
| quantity | INTEGER | 수량 |
| unit_price | INTEGER | 단가 |
| amount | INTEGER | 금액 |
| memo | TEXT | 메모 |

MVP에서는 `orchid_group_id`를 사용하지 않아도 된다. MVP 이후 판매 수량 자동 차감을 위해 nullable FK로 둔다.

---

## 3. 초기 시드 데이터

초기 실행 시 다음 데이터를 자동 생성한다.

```text
House 1~15
각 House마다 PhysicalBed 1~3
각 PhysicalBed의 display_order는 실제 농장 기준 좌→우 순서로 1~3
각 PhysicalBed마다 BedZone LEFT / RIGHT
```

예시:

```text
1동
- 1배드 좌
- 1배드 우
- 2배드 좌
- 2배드 우
- 3배드 좌
- 3배드 우
```

개발용 예시 난 묶음:

```text
3동 2번 배드 좌측 구역
- 카틀레야 A / 120분
- 카틀레야 B / 80분
- 덴드로비움 C / 200분
```

개발용 예시 작업 이력:

```text
최근 농약: 2026-06-01
최근 비료: 2026-05-20
최근 분갈이: 2026-03-12
최근 위치 이동: 2026-06-10
```

---

## 4. 인덱스 전략

권장 인덱스:

```sql
CREATE INDEX idx_physical_beds_house_id ON physical_beds(house_id);
CREATE INDEX idx_bed_zones_physical_bed_id ON bed_zones(physical_bed_id);
CREATE INDEX idx_bed_zones_active ON bed_zones(is_active);
CREATE INDEX idx_orchid_groups_bed_zone_id ON orchid_groups(bed_zone_id);
CREATE INDEX idx_orchid_groups_status ON orchid_groups(status);
CREATE INDEX idx_work_records_target ON work_records(target_type, target_id);
CREATE INDEX idx_work_records_type_date ON work_records(work_type, work_date);
CREATE INDEX idx_work_records_from_zone ON work_records(from_bed_zone_id);
CREATE INDEX idx_work_records_to_zone ON work_records(to_bed_zone_id);
CREATE INDEX idx_sales_slips_customer_id ON sales_slips(customer_id);
CREATE INDEX idx_sales_slips_sale_date ON sales_slips(sale_date);
CREATE INDEX idx_sales_slip_items_orchid_group_id ON sales_slip_items(orchid_group_id);
```

---

## 5. JPA 구현 메모

- `workType`, `status`, `genus`, `paymentStatus`, `salesStatus`, `targetType`, `zoneType`, `side`는 초기에는 문자열 또는 Enum 중 선택한다.

---

## 배드 정밀 배치 테이블

### bed_zone_segments

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGSERIAL PK | 구간 ID |
| bed_zone_id | BIGINT FK | 논리 구역 |
| name | VARCHAR | 구간명 |
| segment_type | VARCHAR | START/MIDDLE/END/CUSTOM |
| sort_order | INTEGER | 표시 순서 |
| memo | TEXT NULL | 간섭 메모 |

### bed_zone_segment_capacities

| 컬럼 | 타입 | 설명 |
|---|---|---|
| bed_zone_segment_id | BIGINT FK | 배드 구간 |
| placement_type | VARCHAR | 판/화분 배치 규격 |
| pot_size | VARCHAR NULL | 화분 크기, NULL은 공통 |
| capacity_mode | VARCHAR | 배치 모드 |
| capacity_value | INTEGER | 수용 가능한 점유 단위 |
| is_allowed | BOOLEAN | 배치 허용 여부 |

### orchid_group_segment_placements

| 컬럼 | 타입 | 설명 |
|---|---|---|
| orchid_group_id | BIGINT FK | 난 묶음 |
| bed_zone_segment_id | BIGINT FK | 실제 배치 구간 |
| quantity | INTEGER | 구간 배치 수량 |
| tray_count | INTEGER NULL | 판/점유 단위 수 |
| placement_mode | VARCHAR | 확정 배치 모드 |
| reorganize_due_date | DATE NULL | 임시 배치 재정리일 |
| memo | TEXT NULL | 배치 메모 |

`orchid_groups.split_placement_allowed`를 nullable BOOLEAN으로 추가한다. 기존 데이터의 NULL은 false로 해석한다.
- `PhysicalBed.displayOrder`는 실제 농장의 좌→우 순서를 보존한다. 화면에서 회전 보기를 제공하더라도 데이터의 기준 순서는 변경하지 않는다.
- 운영 안정성을 위해 Java enum을 사용하는 편이 안전하다.
- 단, 현장 용어가 자주 바뀔 수 있는 항목은 별도 코드 테이블로 확장할 수 있다.
- 작업 유형은 MVP 이후 커스텀 추가 가능성이 높으므로 `work_types` 테이블 전환을 고려한다.
- 난 묶음 이동은 `OrchidGroup.bedZone` 변경과 `WorkRecord` 생성이 하나의 트랜잭션 안에서 처리되어야 한다.
- 판매 수량 차감 기능이 추가되면 전표 상태 변경, 난 묶음 수량 변경, 재고 변동 이력을 하나의 트랜잭션으로 처리해야 한다.

---

## 6. 출하·경매 추적 테이블

| 테이블 | 핵심 컬럼 | 역할 |
|---|---|---|
| `auction_shipments` | `shipment_date`, `auction_market`, `status` | 출하일·경매장 묶음 |
| `auction_shipment_lots` | `shipment_id`, `item_name`, `variety_name`, `shipment_grade`, `boxes`(NULL 허용), `shipped_quantity`, `sold_quantity`, `waiting_quantity`, `returned_quantity`, `current_status` | 실제 추적 lot |
| `auction_attempts` | `shipment_lot_id`, `auction_date`, `attempt_no`, `attempt_status`, `failed_reason` | 경매 시도 |
| `auction_result_lines` | `auction_attempt_id`, `auction_date`, `auction_grade`, `quantity`, `unit_price`, `amount`, `inspection_status` | 단가별 경매 결과 |
| `auction_lot_status_history` | `shipment_lot_id`, `previous_status`, `new_status`, `changed_at`, `reason`, `worker`, `memo` | 상태·보정 이력 |

권장 인덱스:

```sql
CREATE INDEX idx_auction_shipments_date_market ON auction_shipments(shipment_date, auction_market);
CREATE INDEX idx_auction_lots_business_key ON auction_shipment_lots(variety_name, shipment_grade, current_status);
CREATE INDEX idx_auction_attempts_lot_date ON auction_attempts(shipment_lot_id, auction_date);
```

CSV Import 기능 제거에 따라 `import_batches`, `import_rows` 테이블과 경매 테이블의 원본 행 연결 컬럼을 제거한다.

기존 DB는 nullable 완화가 자동 반영되지 않을 수 있으므로 1회 `ALTER TABLE auction_shipment_lots ALTER COLUMN boxes DROP NOT NULL;`을 적용한다.

### 판매 전표 경매 연결

```text
sales_slips.sales_type                 DIRECT | AUCTION, 기존 NULL은 DIRECT
sales_slips.auction_shipment_id        nullable unique FK
sales_slip_items.auction_shipment_lot_id nullable unique FK
```

경매 출하 기록과 lot은 판매 전표에 중복 연결하지 않는다. 경매 전표 최초 금액은 0원이며 정산 결과 자동 반영은 별도 트랜잭션 설계 후 추가한다.

`auction_shipment_lots.current_status`에는 반환 관련 상태로 `RETURN_INFERRED`, `PARTIALLY_RETURNED`, `RETURNED`를 저장한다. 부분반환 시 `returned_quantity`만 증가하고 잔여 수량은 `waiting_quantity`에 유지한다.

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate base master seed SQL for the final farm DB schema.

This replaces the old Spring Boot CommandLineRunner seed initializers:
- WorkTypeSeedDataInitializer
- FarmSeedDataInitializer

Generated data:
- work_types default rows
- 15 houses
- 3 physical beds per house
- LEFT/RIGHT default bed zones per physical bed
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

from seed_common import sql_literal


@dataclass(frozen=True)
class DefaultWorkType:
    code: str
    name: str
    template: str
    system_type: bool
    sort_order: int


DEFAULT_WORK_TYPES: tuple[DefaultWorkType, ...] = (
    DefaultWorkType("INBOUND", "입고", "MEMO", False, 0),
    DefaultWorkType("POTTING", "포트 작업", "REPOT", False, 1),
    DefaultWorkType("PESTICIDE", "농약", "PESTICIDE", False, 2),
    DefaultWorkType("FERTILIZER", "비료", "FERTILIZER", False, 3),
    DefaultWorkType("REPOT", "분갈이", "REPOT", False, 4),
    DefaultWorkType("STATUS", "상태 기록", "STATUS", False, 5),
    DefaultWorkType("MEMO", "일반 메모", "MEMO", False, 6),
    DefaultWorkType("LEAF_CLEANUP", "잎 정리", "CLEANUP", False, 7),
    DefaultWorkType("WEED_CLEANUP", "잡초 정리", "CLEANUP", False, 8),
    DefaultWorkType("FLOWER_CLEANUP", "단화/꽃 정리", "CLEANUP", False, 9),
    DefaultWorkType("MOVEMENT", "위치 이동", "MOVEMENT", True, 10),
)


BED_ZONE_DEFAULTS: tuple[tuple[str, str, int], ...] = (
    ("LEFT", "좌측", 1),
    ("RIGHT", "우측", 2),
)


def generate_work_types_sql() -> str:
    rows = []
    for work_type in DEFAULT_WORK_TYPES:
        rows.append(
            "  ("
            + ", ".join(
                [
                    sql_literal(work_type.code),
                    sql_literal(work_type.name),
                    sql_literal(work_type.template),
                    "TRUE",  # is_active
                    sql_literal(work_type.system_type),
                    "TRUE",  # is_default
                    str(work_type.sort_order),
                    "CURRENT_TIMESTAMP",
                    "CURRENT_TIMESTAMP",
                ]
            )
            + ")"
        )

    return "\n".join(
        [
            "-- Default work types",
            "INSERT INTO work_types (",
            "    code,",
            "    name,",
            "    template,",
            "    is_active,",
            "    is_system,",
            "    is_default,",
            "    sort_order,",
            "    created_at,",
            "    updated_at",
            ") VALUES",
            ",\n".join(rows),
            "ON CONFLICT (code) DO UPDATE SET",
            "    name = EXCLUDED.name,",
            "    template = EXCLUDED.template,",
            "    is_active = EXCLUDED.is_active,",
            "    is_system = EXCLUDED.is_system,",
            "    is_default = EXCLUDED.is_default,",
            "    sort_order = EXCLUDED.sort_order,",
            "    updated_at = CURRENT_TIMESTAMP;",
        ]
    )


def generate_farm_structure_sql() -> str:
    zone_values = ",\n        ".join(
        f"({sql_literal(side)}, {sql_literal(name)}, {sort_order})"
        for side, name, sort_order in BED_ZONE_DEFAULTS
    )

    return f"""-- Farm structure: 15 houses, 3 physical beds per house, 2 default zones per bed
INSERT INTO houses (
    created_at,
    updated_at,
    memo,
    name,
    number
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL,
    house_number::text || '동',
    house_number
FROM generate_series(1, 15) AS house_series(house_number)
ON CONFLICT (number) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO physical_beds (
    created_at,
    updated_at,
    display_order,
    length_cm,
    memo,
    number,
    support_interval_cm,
    width_cm,
    wire_count,
    position_unit_count,
    position_unit_label,
    house_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    bed_number,
    NULL,
    NULL,
    bed_number,
    NULL,
    NULL,
    NULL,
    CASE WHEN bed_number = 3 THEN 28.00 ELSE 24.00 END,
    '치',
    houses.id
FROM houses
CROSS JOIN generate_series(1, 3) AS bed_series(bed_number)
WHERE houses.number BETWEEN 1 AND 15
ON CONFLICT (house_id, number) DO UPDATE SET
    display_order = EXCLUDED.display_order,
    position_unit_count = EXCLUDED.position_unit_count,
    position_unit_label = EXCLUDED.position_unit_label,
    updated_at = CURRENT_TIMESTAMP;

WITH zone_defaults(side, name, sort_order) AS (
    VALUES
        {zone_values}
), target_zones AS (
    SELECT
        physical_beds.id AS physical_bed_id,
        zone_defaults.side,
        zone_defaults.name,
        zone_defaults.sort_order
    FROM physical_beds
    JOIN houses ON houses.id = physical_beds.house_id
    CROSS JOIN zone_defaults
    WHERE houses.number BETWEEN 1 AND 15
      AND physical_beds.number BETWEEN 1 AND 3
)
UPDATE bed_zones
SET
    name = target_zones.name,
    sort_order = target_zones.sort_order,
    zone_type = 'DEFAULT',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
FROM target_zones
WHERE bed_zones.physical_bed_id = target_zones.physical_bed_id
  AND bed_zones.side = target_zones.side;

WITH zone_defaults(side, name, sort_order) AS (
    VALUES
        {zone_values}
), target_zones AS (
    SELECT
        physical_beds.id AS physical_bed_id,
        zone_defaults.side,
        zone_defaults.name,
        zone_defaults.sort_order
    FROM physical_beds
    JOIN houses ON houses.id = physical_beds.house_id
    CROSS JOIN zone_defaults
    WHERE houses.number BETWEEN 1 AND 15
      AND physical_beds.number BETWEEN 1 AND 3
)
INSERT INTO bed_zones (
    created_at,
    updated_at,
    is_active,
    memo,
    name,
    side,
    sort_order,
    zone_type,
    physical_bed_id
)
SELECT
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TRUE,
    NULL,
    target_zones.name,
    target_zones.side,
    target_zones.sort_order,
    'DEFAULT',
    target_zones.physical_bed_id
FROM target_zones
WHERE NOT EXISTS (
    SELECT 1
    FROM bed_zones
    WHERE bed_zones.physical_bed_id = target_zones.physical_bed_id
      AND bed_zones.side = target_zones.side
);"""


def generate_sql() -> str:
    return "\n".join(
        [
            "-- Generated by generate_base_master_seed.py",
            "-- Base master data converted from WorkTypeSeedDataInitializer and FarmSeedDataInitializer.",
            "-- Includes work_types and default farm structure only.",
            "BEGIN;",
            "",
            generate_work_types_sql(),
            "",
            generate_farm_structure_sql(),
            "",
            "COMMIT;",
            "",
        ]
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate base master seed SQL.")
    parser.add_argument("--output", required=True, type=Path, help="Output SQL path")
    args = parser.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(generate_sql(), encoding="utf-8")
    print("Generated:", args.output)
    print(f"Work types: {len(DEFAULT_WORK_TYPES)}")
    print("Houses: 15")
    print("Physical beds: 45")
    print("Bed zones: 90")


if __name__ == "__main__":
    main()

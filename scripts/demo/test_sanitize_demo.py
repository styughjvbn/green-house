from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from sanitize_demo import (
    CatalogPair,
    SanitizationError,
    catalog_pair_for,
    load_catalog,
    sanitize_json,
    shift_iso_date,
    shift_iso_instant,
    transform_state_chain_manifest,
    unique_catalog_mapping,
)


class CatalogTest(unittest.TestCase):
    def test_repository_catalog_is_valid_and_large_enough(self) -> None:
        catalog = load_catalog()
        self.assertGreaterEqual(len(catalog), 1_000)
        self.assertEqual(len(catalog), len(set(catalog)))

    def test_mapping_is_deterministic_and_unique(self) -> None:
        catalog = [
            CatalogPair("카틀레야", "레드"),
            CatalogPair("호접란", "화이트"),
            CatalogPair("심비디움", "그린"),
        ]
        rows = [(7, "원본속", "원본A"), (3, "원본속", "원본B")]
        first = unique_catalog_mapping(rows, catalog, "k" * 32)
        second = unique_catalog_mapping(list(reversed(rows)), catalog, "k" * 32)
        self.assertEqual(first, second)
        self.assertEqual(len(set(first.values())), len(rows))

    def test_pair_lookup_is_deterministic(self) -> None:
        catalog = [CatalogPair("A", "1"), CatalogPair("B", "2")]
        self.assertEqual(
            catalog_pair_for(catalog, "k" * 32, "lot", "source"),
            catalog_pair_for(catalog, "k" * 32, "lot", "source"),
        )

    def test_invalid_catalog_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps([{"item": "", "varieties": []}]), encoding="utf-8")
            with self.assertRaises(SanitizationError):
                load_catalog(path)


class JsonSanitizationTest(unittest.TestCase):
    def test_structure_and_safe_codes_are_preserved(self) -> None:
        source = {"name": "홍길동", "status": "COMPLETED", "values": [1, True, "메모"]}
        self.assertEqual(
            sanitize_json(source),
            {"name": "데모", "status": "COMPLETED", "values": [1, True, "데모"]},
        )

    def test_manifest_transformation_preserves_links_and_changes_state(self) -> None:
        payload = {
            "manifest_schema_version": 2,
            "mutations": [
                {
                    "mutation_key": "source-key",
                    "mutation_type": "CHANGE",
                    "source_type": "WORK_EFFECT",
                    "source_reference": "effect:7",
                    "occurred_at": "2026-09-01T00:00:00.000000Z",
                    "effective_business_date": "2026-09-01",
                    "reason": "원본 사유",
                    "evidence": {"work_effect_ids": [7], "source_rows": [99]},
                    "entries": [
                        {
                            "orchid_group_id": "3",
                            "before_state": {
                                "quantity": 2,
                                "reservedQuantity": 1,
                                "trayCount": None,
                                "varietyId": 5,
                                "genus": "원본속",
                                "varietyName": "원본품종",
                                "memo": "원본 메모",
                            },
                            "after_state": None,
                        }
                    ],
                }
            ],
        }
        transformed, cutover_key = transform_state_chain_manifest(
            payload,
            {5: CatalogPair("데모속", "데모품종")},
            [CatalogPair("대체속", "대체품종")],
            "k" * 32,
            10,
            3,
        )
        mutation = transformed["mutations"][0]
        snapshot = mutation["entries"][0]["before_state"]
        self.assertNotEqual(mutation["mutation_key"], "source-key")
        self.assertEqual(mutation["evidence"], {"work_effect_ids": [7]})
        self.assertEqual(mutation["reason"], "데모 전환 이력")
        self.assertEqual(snapshot["quantity"], 6)
        self.assertEqual(snapshot["reservedQuantity"], 3)
        self.assertEqual(snapshot["genus"], "데모속")
        self.assertIsNone(snapshot["memo"])
        self.assertRegex(cutover_key, r"^[0-9a-f-]{36}$")

    def test_iso_values_are_shifted_without_losing_utc(self) -> None:
        self.assertEqual(shift_iso_date("2026-09-01", -2), "2026-08-30")
        self.assertEqual(
            shift_iso_instant("2026-09-01T03:04:05.000000Z", 2),
            "2026-09-03T03:04:05.000000Z",
        )


if __name__ == "__main__":
    unittest.main()

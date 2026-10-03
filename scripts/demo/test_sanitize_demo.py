from __future__ import annotations

import json
import hashlib
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from sanitize_demo import (
    CatalogPair,
    PRESERVED_TEXT_COLUMNS,
    SanitizationError,
    assert_original_values_removed,
    canonical_json,
    catalog_pair_for,
    collect_original_sensitive_values,
    load_catalog,
    transform_business_json,
    unique_catalog_mapping,
    json_scaling_fits,
    INT_MAX,
    choose_scaling_factors,
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
    def transform(self, value: object) -> object:
        return transform_business_json(
            value, key="k" * 32, namespace="test", quantity_factor=3, price_factor=2,
            master_mapping={5: CatalogPair("데모속", "데모품종")},
            catalog=[CatalogPair("대체속", "대체품종")], date_shift_days=10,
        )

    def test_correction_audit_preserves_status_dates_and_quantity_balances(self) -> None:
        result = self.transform({
            "beforeWorkDate": "2026-09-01", "afterWorkDate": [2026, 9, 2],
            "adjustments": [{"orchidGroupId": 348, "beforeQuantity": 850,
                             "afterQuantity": 1050, "beforeStatus": "정상", "afterStatus": "생성 취소"}],
            "quantityBalances": [{"sourceInputQuantities": {"185": 1000},
                                  "resultQuantities": {"348": 1050},
                                  "lossQuantity": 150, "increaseQuantity": 200,
                                  "executionId": 447}],
            "worker": "개인 작업자", "reason": "개인 사유",
        })
        self.assertEqual(result["beforeWorkDate"], "2026-09-11")
        self.assertEqual(result["afterWorkDate"], [2026, 9, 12])
        self.assertEqual(result["adjustments"][0], {
            "orchidGroupId": 348, "beforeQuantity": 2550, "afterQuantity": 3150,
            "beforeStatus": "정상", "afterStatus": "생성 취소",
        })
        self.assertEqual(result["quantityBalances"][0], {
            "sourceInputQuantities": {"185": 3000}, "resultQuantities": {"348": 3150},
            "lossQuantity": 450, "increaseQuantity": 600, "executionId": 447,
        })
        self.assertIsNone(result["worker"])
        self.assertIsNone(result["reason"])

    def test_identity_migration_snapshot_handles_database_column_names(self) -> None:
        self.assertEqual(self.transform({
            "id": 348, "variety_id": 5, "genus": "실제 속", "variety_name": "실제 품종",
            "quantity": 100, "reserved_quantity": 10, "tray_count": 5,
            "pot_size_code": "POT_3", "created_at": "2026-09-01T12:34:56Z",
            "memo": "실제 메모",
        }), {
            "id": 348, "variety_id": 5, "genus": "데모속", "variety_name": "데모품종",
            "quantity": 300, "reserved_quantity": 30, "tray_count": 15,
            "pot_size_code": "POT_3", "created_at": "2026-09-11T12:34:56Z", "memo": None,
        })

    def test_effect_results_scale_quantities_without_changing_keys_ids_or_counts(self) -> None:
        result = self.transform({
            "executionKey": "execution-key-348", "fromBedZoneId": "12",
            "actualQuantity": 100, "inputQuantity": 110, "totalInputQuantity": 110,
            "discardedQuantity": 10, "remainingQuantity": 100, "resultCount": 1,
            "createdOrchidGroupIds": [348],
        })
        self.assertEqual(result, {
            "executionKey": "execution-key-348", "fromBedZoneId": "12",
            "actualQuantity": 300, "inputQuantity": 330, "totalInputQuantity": 330,
            "discardedQuantity": 30, "remainingQuantity": 300, "resultCount": 1,
            "createdOrchidGroupIds": [348],
        })

    def test_snapshot_scaling_checks_historical_values_and_quantity_maps(self) -> None:
        self.assertFalse(json_scaling_fits({"beforeQuantity": INT_MAX}, 2, 2))
        self.assertFalse(json_scaling_fits({"sourceInputQuantities": {"348": INT_MAX}}, 2, 2))
        self.assertTrue(json_scaling_fits({"orchidGroupId": INT_MAX, "quantity": 10}, 2, 2))

    def test_scaling_factor_selection_accounts_for_historical_json(self) -> None:
        def rows(cursor: object, query: str, params: object = ()) -> list[tuple]:
            if query.startswith("SELECT coalesce(max"):
                return [(0,)]
            if query == "SELECT result_details FROM work_operation_corrections":
                return [({"adjustments": [{"beforeQuantity": INT_MAX}]},)]
            return []

        with patch("sanitize_demo.fetch_all", side_effect=rows):
            self.assertEqual(choose_scaling_factors(object(), 3, 2), (1, 2))

    def test_invalid_dates_or_quantity_maps_fail_closed(self) -> None:
        with self.assertRaises(SanitizationError):
            self.transform({"workDate": "실제 개인 정보"})
        with self.assertRaises(SanitizationError):
            self.transform({"sourceInputQuantities": {"348": "개인 정보"}})

    def test_active_engine_snapshot_transformation_preserves_shape(self) -> None:
        source = {
            "quantity": 2,
            "reservedQuantity": 1,
            "varietyId": 5,
            "genus": "원본속",
            "varietyName": "원본품종",
            "status": "정상",
            "memo": "원본 메모",
            "workType": "자리 이동",
            "workTypeCode": "MOVEMENT",
            "workTypeName": "자리 이동",
        }
        transformed = transform_business_json(
            source,
            key="k" * 32,
            namespace="entry-1",
            quantity_factor=3,
            price_factor=2,
            master_mapping={5: CatalogPair("데모속", "데모품종")},
            catalog=[CatalogPair("대체속", "대체품종")],
        )
        self.assertEqual(set(transformed), set(source))
        self.assertEqual(transformed["quantity"], 6)
        self.assertEqual(transformed["reservedQuantity"], 3)
        self.assertEqual(transformed["genus"], "데모속")
        self.assertEqual(transformed["varietyName"], "데모품종")
        self.assertEqual(transformed["status"], "정상")
        self.assertIsNone(transformed["memo"])
        self.assertEqual(transformed["workType"], "자리 이동")
        self.assertEqual(transformed["workTypeCode"], "MOVEMENT")
        self.assertEqual(transformed["workTypeName"], "자리 이동")

    def test_canonical_json_sorts_keys_for_fingerprint(self) -> None:
        self.assertEqual(
            canonical_json({"z": 1, "a": {"y": True, "x": None}}),
            '{"a":{"x":null,"y":true},"z":1}',
        )

    def test_java_local_date_fingerprint_representation_is_stable(self) -> None:
        payload = {
            "cutoverKey": "00000000-0000-0000-0000-000000000001",
            "effectiveBusinessDate": [2026, 9, 14],
            "groups": [],
        }
        self.assertEqual(
            hashlib.sha256(canonical_json(payload).encode("utf-8")).hexdigest(),
            "ccb14952650006f1bb2d72dbe84f7db570d163b55f0c257048587f1885444167",
        )


class PipelineContractTest(unittest.TestCase):
    def test_work_type_columns_are_preserved_without_weakening_sensitive_sources(self) -> None:
        class Cursor:
            query = ""

            def execute(self, query: str, params: object = ()) -> None:
                self.query = query

            def fetchall(self) -> list[tuple[str]]:
                if "FROM audit_events" in self.query and "actor_id" in self.query:
                    return [("자리 이동",), ("실제 작업자",)]
                return []

        values = collect_original_sensitive_values(Cursor())
        self.assertIn("자리 이동", values)
        self.assertIn("실제 작업자", values)
        self.assertEqual(
            PRESERVED_TEXT_COLUMNS,
            {("work_types", "name"), ("work_records", "work_type")},
        )

        script = (Path(__file__).parent / "sanitize_demo.py").read_text(encoding="utf-8")
        self.assertNotIn("UPDATE work_types SET name=%s", script)
        self.assertNotIn("UPDATE work_records SET work_type=%s", script)

        class ValidationCursor:
            query = ""
            queries: list[str] = []

            def execute(self, query: str, params: object = ()) -> None:
                self.query = query
                self.queries.append(query)

            def fetchall(self) -> list[tuple[str, ...]]:
                if "FROM information_schema.columns" in self.query:
                    return [
                        ("work_types", "name"),
                        ("work_records", "work_type"),
                        ("audit_events", "actor_id"),
                    ]
                if "FROM audit_events" in self.query:
                    return [("작업자 001",)]
                return []

        validation_cursor = ValidationCursor()
        assert_original_values_removed(validation_cursor, {"자리 이동"})
        self.assertFalse(any("FROM work_types" in query for query in validation_cursor.queries))
        self.assertFalse(
            any("FROM work_records" in query for query in validation_cursor.queries)
        )

    def test_restore_validates_but_never_migrates_sanitized_source(self) -> None:
        script = (Path(__file__).parent / "restore-temp-db.sh").read_text(encoding="utf-8")
        self.assertIn("flyway validate", script)
        self.assertNotIn("flyway migrate", script)

    def test_promotion_uses_fixed_blue_green_names_and_confirmation(self) -> None:
        script = (Path(__file__).parent / "refresh-demo-db.sh").read_text(encoding="utf-8")
        for value in (
            "greenhouse_demo",
            "greenhouse_demo_next",
            "greenhouse_demo_prev",
            "greenhouse_demo_template",
        ):
            self.assertIn(value, script)
        self.assertIn(
            "greenhouse_demo:greenhouse_demo_next:greenhouse_demo_prev",
            script,
        )
        self.assertLess(
            script.index('"${SCRIPT_DIR}/validate-demo-db.sh"'),
            script.rindex("  stop_backend\n"),
        )
        self.assertIn("greenhouse_demo_refresh", script)
        self.assertIn("pg_signal_backend", script)
        self.assertIn("false:true:true:true", script)
        self.assertIn("WITH targets AS MATERIALIZED", script)
        self.assertIn("activity.backend_type='client backend'", script)
        self.assertIn('--template="${DEMO_DB_TEMPLATE_NAME}"', script)
        self.assertIn("public must be owned by", script)
        self.assertIn("role-level temp_file_limit=128MB", script)
        self.assertNotIn(
            "IN DATABASE ${name} SET temp_file_limit",
            script,
        )
        self.assertNotIn("must target postgres as a superuser", script)

    def test_candidate_flyway_validation_uses_pinned_docker_image(self) -> None:
        script = (Path(__file__).parent / "validate-demo-db.sh").read_text(encoding="utf-8")
        self.assertIn('FLYWAY_IMAGE="redgate/flyway:11"', script)
        self.assertIn("docker run --rm --network host", script)
        self.assertIn("--env FLYWAY_PASSWORD", script)
        self.assertIn('"${MIGRATION_DIR}:/flyway/sql:ro"', script)
        self.assertNotIn("require_command flyway", script)

    def test_sanitized_dump_uses_home_staging_before_publication(self) -> None:
        script = (Path(__file__).parent / "create-sanitized-demo-dump.sh").read_text(
            encoding="utf-8"
        )
        self.assertIn("DEMO_DOCKER_STAGING_DIR", script)
        self.assertIn('${HOME}/green-house-demo-refresh-staging', script)
        self.assertIn(
            '"${SCRIPT_DIR}/docker-sanitize.sh" "${raw_dump}" "${staged_output}"',
            script,
        )
        self.assertIn('sha256sum --check', script)
        self.assertLess(script.index('sha256sum --check'), script.index('mv -- "${partial_output}"'))


if __name__ == "__main__":
    unittest.main()

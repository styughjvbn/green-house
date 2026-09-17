from __future__ import annotations

import json
import hashlib
import tempfile
import unittest
from pathlib import Path

from sanitize_demo import (
    CatalogPair,
    SanitizationError,
    canonical_json,
    catalog_pair_for,
    load_catalog,
    transform_business_json,
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
    def test_active_engine_snapshot_transformation_preserves_shape(self) -> None:
        source = {
            "quantity": 2,
            "reservedQuantity": 1,
            "varietyId": 5,
            "genus": "원본속",
            "varietyName": "원본품종",
            "status": "정상",
            "memo": "원본 메모",
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
    def test_restore_validates_but_never_migrates_sanitized_source(self) -> None:
        script = (Path(__file__).parent / "restore-temp-db.sh").read_text(encoding="utf-8")
        self.assertIn("flyway validate", script)
        self.assertNotIn("flyway migrate", script)

    def test_promotion_uses_fixed_blue_green_names_and_confirmation(self) -> None:
        script = (Path(__file__).parent / "refresh-demo-db.sh").read_text(encoding="utf-8")
        for value in ("greenhouse_demo", "greenhouse_demo_next", "greenhouse_demo_prev"):
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

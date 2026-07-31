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


if __name__ == "__main__":
    unittest.main()

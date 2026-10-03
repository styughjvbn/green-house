from __future__ import annotations

import copy
from pathlib import Path
import tempfile
import unittest

import yaml

from generate_openapi import write_openapi
from split_openapi_slices import dump_yaml, split_openapi


class OpenApiGenerationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.document = {
            "openapi": "3.1.0",
            "info": {"title": "Test API", "version": "v0"},
            "paths": {
                "/api/groups": {
                    "get": {
                        "tags": ["orchid-group-command-controller"],
                        "responses": {
                            "200": {
                                "description": "OK",
                                "content": {
                                    "application/json": {
                                        "schema": {"$ref": "#/components/schemas/Page"}
                                    }
                                },
                            }
                        },
                    }
                }
            },
            "components": {
                "schemas": {
                    "Page": {
                        "type": "object",
                        "properties": {
                            "first": {"type": "boolean"},
                            "last": {"type": "boolean"},
                            "numberOfElements": {"type": "integer"},
                            "content": {
                                "type": "array",
                                "items": {
                                    "type": "object",
                                    "properties": {
                                        "quantity": {"type": "integer"},
                                        "id": {"type": "integer"},
                                    },
                                },
                            },
                        },
                        "required": ["last", "first"],
                        "examples": [{"content": [3, 1, 2]}],
                        "allOf": [{"description": "second"}, {"description": "first"}],
                    }
                }
            },
        }

    def reordered_document(self) -> dict:
        document = copy.deepcopy(self.document)
        schema = document["components"]["schemas"]["Page"]
        schema["properties"] = dict(reversed(list(schema["properties"].items())))
        items = schema["properties"]["content"]["items"]
        items["properties"] = dict(reversed(list(items["properties"].items())))
        return document

    def test_property_order_does_not_change_yaml(self) -> None:
        self.assertEqual(dump_yaml(self.document), dump_yaml(self.reordered_document()))

    def test_serialization_preserves_contract_arrays_and_input(self) -> None:
        original = copy.deepcopy(self.document)
        result = yaml.safe_load(dump_yaml(self.document))
        self.assertEqual(original, result)
        self.assertEqual(original, self.document)
        self.assertEqual(dump_yaml(result), dump_yaml(self.document))
        properties = result["components"]["schemas"]["Page"]["properties"]
        self.assertEqual(list(properties), sorted(properties))
        self.assertEqual(list(properties["content"]["items"]["properties"]), ["id", "quantity"])

    def test_full_document_and_slices_have_stable_output(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            outputs = []
            for index, document in enumerate([self.document, self.reordered_document()]):
                output = root / str(index) / "openapi.yaml"
                slices = output.parent / "slices"
                write_openapi(document, output, "http://localhost:8080")
                split_openapi(output, slices)
                outputs.append({
                    "openapi.yaml": output.read_bytes(),
                    **{path.name: path.read_bytes() for path in slices.glob("*.yaml")},
                })
            self.assertEqual(outputs[0], outputs[1])


if __name__ == "__main__":
    unittest.main()

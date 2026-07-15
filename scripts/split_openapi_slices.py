#!/usr/bin/env python3
"""Split the implementation-generated OpenAPI document by controller tag."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import yaml


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OPENAPI_PATH = REPOSITORY_ROOT / "docs/api/openapi.yaml"
DEFAULT_SLICE_DIR = REPOSITORY_ROOT / "docs/api/slices"
HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}

DOMAIN_MAP = dict(
    [
        ("auth", ["auth-controller"]),
        ("farm-structure", ["farm-structure-controller", "orchid-group-query-controller"]),
        ("farm-status", ["farm-status-controller", "dashboard-controller"]),
        ("orchid-command", [
            "orchid-group-command-controller",
            "bed-placement-controller",
            "multi-create-work-operation-controller",
            "repot-work-operation-controller",
        ]),
        ("inventory", ["variety-controller", "material-controller", "inbound-record-controller"]),
        ("orchid-collection", ["orchid-group-collection-controller"]),
        ("derived-orchid-group", ["derived-orchid-group-controller"]),
        ("work", ["work-record-controller"]),
        ("work-operation", ["work-operation-controller"]),
        ("partner", ["business-partner-controller", "partner-settlement-settings-controller"]),
        ("sales", ["sales-controller", "print-controller"]),
        ("analytics", ["analytics-controller"]),
        ("auction", ["auction-tracking-controller", "auction-settlement-controller"]),
        ("payment", ["payment-controller"]),
    ]
)


def collect_component_refs(value: Any, refs: set[tuple[str, str]] | None = None) -> set[tuple[str, str]]:
    if refs is None:
        refs = set()
    if isinstance(value, dict):
        ref = value.get("$ref")
        prefix = "#/components/"
        if isinstance(ref, str) and ref.startswith(prefix):
            parts = ref[len(prefix) :].split("/", 1)
            if len(parts) == 2:
                refs.add((parts[0], parts[1]))
        for nested in value.values():
            collect_component_refs(nested, refs)
    elif isinstance(value, list):
        for nested in value:
            collect_component_refs(nested, refs)
    return refs


def component_closure(
    components: dict[str, dict[str, Any]], seed_refs: set[tuple[str, str]]
) -> set[tuple[str, str]]:
    refs = set(seed_refs)
    pending = list(seed_refs)
    while pending:
        section, name = pending.pop()
        component = components.get(section, {}).get(name)
        if component is None:
            raise ValueError(f"Missing component referenced by OpenAPI: {section}/{name}")
        for nested_ref in collect_component_refs(component):
            if nested_ref not in refs:
                refs.add(nested_ref)
                pending.append(nested_ref)
    return refs


def operation_key(path: str, method: str) -> str:
    return f"{method.upper()} {path}"


def validate_assignments(openapi: dict[str, Any]) -> None:
    tag_to_slice = {
        tag: slice_name
        for slice_name, tags in DOMAIN_MAP.items()
        for tag in tags
    }
    errors: list[str] = []

    for path, path_item in openapi.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS:
                continue
            slices = {
                tag_to_slice[tag]
                for tag in operation.get("tags", [])
                if tag in tag_to_slice
            }
            if len(slices) != 1:
                tags = ", ".join(operation.get("tags", [])) or "(none)"
                errors.append(
                    f"{operation_key(path, method)} must map to exactly one slice "
                    f"(tags: {tags}; mapped slices: {', '.join(sorted(slices)) or '(none)'})"
                )

    if errors:
        raise ValueError("OpenAPI slice coverage failed:\n- " + "\n- ".join(errors))


def build_slice(openapi: dict[str, Any], slice_name: str, tags: list[str]) -> dict[str, Any]:
    selected_paths: dict[str, Any] = {}
    seed_refs: set[tuple[str, str]] = set()

    for path, path_item in openapi.get("paths", {}).items():
        selected_item: dict[str, Any] = {}
        for key, value in path_item.items():
            if key not in HTTP_METHODS:
                selected_item[key] = value
        for method, operation in path_item.items():
            if method in HTTP_METHODS and set(operation.get("tags", [])) & set(tags):
                selected_item[method] = operation
                collect_component_refs(operation, seed_refs)
        if any(method in selected_item for method in HTTP_METHODS):
            selected_paths[path] = selected_item
            collect_component_refs(selected_item, seed_refs)

    all_components = openapi.get("components", {})
    refs = component_closure(all_components, seed_refs)
    selected_components: dict[str, Any] = {}
    for section, name in sorted(refs):
        selected_components.setdefault(section, {})[name] = all_components[section][name]

    slice_doc: dict[str, Any] = dict(
        [
            ("openapi", openapi.get("openapi")),
            (
                "info",
                {
                    "title": f"Greenhouse API Slice - {slice_name}",
                    "version": openapi.get("info", {}).get("version", "v0"),
                },
            ),
        ]
    )
    for key in ("jsonSchemaDialect", "servers", "security"):
        if key in openapi:
            slice_doc[key] = openapi[key]
    slice_doc["paths"] = selected_paths
    if selected_components:
        slice_doc["components"] = selected_components
    return slice_doc


def dump_yaml(document: dict[str, Any]) -> str:
    return yaml.safe_dump(
        document,
        allow_unicode=True,
        sort_keys=False,
        width=120,
    )


def split_openapi(openapi_path: Path = DEFAULT_OPENAPI_PATH, slice_dir: Path = DEFAULT_SLICE_DIR) -> None:
    openapi = yaml.safe_load(openapi_path.read_text(encoding="utf-8"))
    validate_assignments(openapi)
    slice_dir.mkdir(parents=True, exist_ok=True)

    for slice_name, tags in DOMAIN_MAP.items():
        output_path = slice_dir / f"{slice_name}.openapi.yaml"
        temporary_path = output_path.with_suffix(output_path.suffix + ".tmp")
        temporary_path.write_text(dump_yaml(build_slice(openapi, slice_name, tags)), encoding="utf-8")
        temporary_path.replace(output_path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_OPENAPI_PATH)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_SLICE_DIR)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    split_openapi(args.input.resolve(), args.output_dir.resolve())
    print(f"Generated {len(DOMAIN_MAP)} OpenAPI slices in {args.output_dir.resolve()}")


if __name__ == "__main__":
    main()

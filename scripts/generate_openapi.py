#!/usr/bin/env python3
"""Generate the full OpenAPI document from Springdoc and split it by domain.

By default this script starts the backend on a temporary local port. To use an
already-running backend instead, pass its Springdoc URL with --url.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time
from typing import Any
from urllib.error import URLError
from urllib.request import Request, urlopen

import yaml

from split_openapi_slices import DEFAULT_OPENAPI_PATH, dump_yaml, split_openapi


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
BACKEND_DIR = REPOSITORY_ROOT / "backend"
DEFAULT_PORT = 18080
DEFAULT_TIMEOUT_SECONDS = 120


def fetch_openapi(url: str, timeout: float = 3) -> dict[str, Any]:
    request = Request(url, headers={"Accept": "application/json"})
    with urlopen(request, timeout=timeout) as response:
        document = json.load(response)
    if not isinstance(document, dict) or "openapi" not in document or "paths" not in document:
        raise ValueError(f"The response from {url} is not an OpenAPI document")
    return document


def wait_for_openapi(url: str, process: subprocess.Popen[bytes], timeout_seconds: int) -> dict[str, Any]:
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Backend exited before Springdoc was ready (exit code {process.returncode})")
        try:
            return fetch_openapi(url)
        except (URLError, TimeoutError, ValueError) as error:
            last_error = error
            time.sleep(1)
    raise TimeoutError(f"Springdoc did not become ready within {timeout_seconds}s: {last_error}")


def stop_process(process: subprocess.Popen[bytes]) -> None:
    if process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=5)


def generate_from_temporary_backend(port: int, timeout_seconds: int) -> dict[str, Any]:
    url = f"http://127.0.0.1:{port}/api-docs"
    environment = os.environ.copy()
    environment.update(
        {
            "SERVER_PORT": str(port),
            "AUTH_ENABLED": "false",
        }
    )

    with tempfile.TemporaryFile() as log:
        process = subprocess.Popen(
            ["./gradlew", "bootRun", "--no-daemon"],
            cwd=BACKEND_DIR,
            env=environment,
            stdout=log,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            return wait_for_openapi(url, process, timeout_seconds)
        except Exception as error:
            log.seek(0)
            output = log.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Could not generate OpenAPI from the backend.\n{output}") from error
        finally:
            stop_process(process)


def write_openapi(document: dict[str, Any], output_path: Path, public_server_url: str) -> None:
    document["servers"] = [{"url": public_server_url, "description": "Generated server url"}]
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temporary_path = output_path.with_suffix(output_path.suffix + ".tmp")
    temporary_path.write_text(dump_yaml(document), encoding="utf-8")
    temporary_path.replace(output_path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--url",
        help="Springdoc JSON URL of an already-running backend; skips temporary backend startup",
    )
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="temporary backend port")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OPENAPI_PATH)
    parser.add_argument("--server-url", default="http://localhost:8080")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    document = fetch_openapi(args.url) if args.url else generate_from_temporary_backend(args.port, args.timeout)
    output_path = args.output.resolve()
    write_openapi(document, output_path, args.server_url)
    split_openapi(output_path)

    operation_count = sum(
        1
        for path_item in document.get("paths", {}).values()
        for method in path_item
        if method in {"get", "put", "post", "delete", "options", "head", "patch", "trace"}
    )
    schema_count = len(document.get("components", {}).get("schemas", {}))
    print(
        f"Generated {output_path} from Springdoc "
        f"({operation_count} operations, {len(document.get('paths', {}))} paths, {schema_count} schemas)"
    )


if __name__ == "__main__":
    main()

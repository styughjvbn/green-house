import assert from "node:assert/strict";
import { test } from "node:test";
import {
  mkdtempSync,
  mkdirSync,
  writeFileSync,
  readFileSync,
  copyFileSync,
  rmSync,
  existsSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { developmentEnvironment } from "./dev-environment.mjs";

test("the configuration catalog covers backend declarations and Kubernetes settings", () => {
  const read = (path) =>
    readFileSync(new URL(`../${path}`, import.meta.url), "utf8");
  const catalog = new Set(
    [
      ...read(".env.example").matchAll(
        /^(?:#\s*(?:\[[^\]]+\]\s*)?)?([A-Z][A-Z0-9_]*)=/gm,
      ),
    ].map((match) => match[1]),
  );
  for (const path of [
    "k8s/base/configmap.yaml",
    "k8s/base/secret.yaml",
    "k8s/overlays/demo/configmap-patch.yaml",
    "k8s/overlays/demo/secret-patch.yaml",
  ])
    for (const match of read(path).matchAll(/^  ([A-Z][A-Z0-9_]*):/gm))
      assert.ok(catalog.has(match[1]), `Document ${match[1]} from ${path}`);
  const aliases = new Set(["FRONTEND_ORIGIN", "FRONTEND_ORIGINS"]);
  for (const match of read(
    "backend/src/main/resources/application.yml",
  ).matchAll(/\$\{([A-Z][A-Z0-9_]*):/g))
    assert.ok(
      catalog.has(match[1]) || aliases.has(match[1]),
      `Document ${match[1]}`,
    );
});

function fixture(run) {
  const root = mkdtempSync(join(tmpdir(), "greenhouse-env-"));
  try {
    run(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test("dotenv preserves exported values, quoting and multiline secrets without shell evaluation", () => {
  fixture((root) => {
    const file = join(root, ".env");
    const marker = join(root, "must-not-exist");
    writeFileSync(
      file,
      `SHARED=file\nQUOTED="a # b"\nMULTILINE="first\nsecond"\nLITERAL=$(touch ${marker})\n`,
    );
    const result = developmentEnvironment(file, {
      SHARED: "exported",
      EMPTY: "",
    });
    assert.equal(result.SHARED, "exported");
    assert.equal(result.QUOTED, "a # b");
    assert.equal(result.MULTILINE, "first\nsecond");
    assert.equal(result.EMPTY, "");
    assert.equal(result.LITERAL, `$(touch ${marker})`);
    assert.equal(existsSync(marker), false);
    assert.deepEqual(
      developmentEnvironment(join(root, "missing"), { KEPT: "yes" }),
      { KEPT: "yes" },
    );
  });
});

test("development startup passes root env and configured ports, and adds LAN CORS only on request", () => {
  fixture((root) => {
    const scripts = join(root, "scripts"),
      bin = join(root, "bin"),
      capture = join(root, "capture.json");
    mkdirSync(scripts);
    mkdirSync(bin);
    for (const file of ["dev-start.sh", "dev-environment.mjs"])
      copyFileSync(new URL(file, import.meta.url), join(scripts, file));
    writeFileSync(
      join(root, ".env"),
      "AI_RUNTIME_SECRET=fixture-secret\nAPP_ENV=prod\nSERVER_PORT=18081\nFRONTEND_ORIGIN_PATTERNS=http://localhost:3000\n",
    );
    for (const [name, text] of Object.entries({
      ss: "#!/bin/sh\nprintf 'LISTEN\\n'\n",
      curl: "#!/bin/sh\nprintf 200\n",
      ip: "#!/bin/sh\nprintf '2: eth0 inet 192.168.0.2/24 scope global eth0\\n'\n",
      npm: "#!/bin/sh\nexit 99\n",
      docker: `#!${process.execPath}\nrequire('node:fs').writeFileSync(${JSON.stringify(capture)}, JSON.stringify({secret:process.env.AI_RUNTIME_SECRET,origin:process.env.FRONTEND_ORIGIN_PATTERNS,mode:process.env.APP_ENV,backend:process.env.BACKEND_API_URL}));\n`,
    }))
      writeFileSync(join(bin, name), text, { mode: 0o755 });
    const env = { ...process.env, PATH: `${bin}:${process.env.PATH}` };
    for (const key of [
      "GREENHOUSE_DEV_ENV_LOADED",
      "AI_RUNTIME_SECRET",
      "FRONTEND_ORIGIN_PATTERNS",
      "FRONTEND_ORIGIN",
      "FRONTEND_ORIGINS",
      "SERVER_PORT",
      "BACKEND_API_URL",
    ])
      delete env[key];
    for (const lan of [false, true]) {
      const result = spawnSync(
        "bash",
        [join(scripts, "dev-start.sh"), ...(lan ? ["--lan"] : [])],
        { env, encoding: "utf8" },
      );
      assert.equal(result.status, 0, result.stdout + result.stderr);
      const seen = JSON.parse(readFileSync(capture, "utf8"));
      assert.equal(seen.secret, "fixture-secret");
      assert.equal(seen.mode, "dev");
      assert.equal(seen.backend, "http://127.0.0.1:18081/api");
      assert.equal(
        seen.origin,
        "http://localhost:3000" + (lan ? ",http://192.168.0.2:3000" : ""),
      );
      assert.ok(
        result.stdout.includes("http://localhost:18081/actuator/health"),
      );
      assert.ok(!result.stdout.includes("fixture-secret"));
    }
  });
});

test("development wrapper returns the child exit status", () => {
  const result = spawnSync(
    process.execPath,
    [
      new URL("dev-environment.mjs", import.meta.url).pathname,
      process.execPath,
      "-e",
      "process.exit(7)",
    ],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 7);
});

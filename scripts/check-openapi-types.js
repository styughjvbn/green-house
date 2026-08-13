const { mkdtempSync, readFileSync, rmSync } = require("node:fs");
const { tmpdir } = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const repositoryRoot = path.resolve(__dirname, "..");
const frontendDirectory = path.join(repositoryRoot, "frontend");
const schemaPath = path.join(repositoryRoot, "docs/api/openapi.yaml");
const checkedInTypesPath = path.join(
  frontendDirectory,
  "src/shared/api/generated/openapi.d.ts",
);
const temporaryDirectory = mkdtempSync(
  path.join(tmpdir(), "greenhouse-openapi-types-"),
);
const generatedTypesPath = path.join(temporaryDirectory, "openapi.d.ts");
const cliPath = path.join(
  frontendDirectory,
  "node_modules/openapi-typescript/bin/cli.js",
);

try {
  const result = spawnSync(
    process.execPath,
    [cliPath, schemaPath, "-o", generatedTypesPath],
    {
      cwd: frontendDirectory,
      encoding: "utf-8",
    },
  );

  if (result.status !== 0) {
    process.stderr.write(result.stderr || result.stdout);
    process.exit(result.status ?? 1);
  }

  const checkedInTypes = readFileSync(checkedInTypesPath, "utf-8");
  const generatedTypes = readFileSync(generatedTypesPath, "utf-8");
  if (checkedInTypes !== generatedTypes) {
    console.error(
      "OpenAPI TypeScript types are stale. Run `npm run api:types` in frontend.",
    );
    process.exit(1);
  }

  console.log("OpenAPI TypeScript types are up to date");
} finally {
  rmSync(temporaryDirectory, { recursive: true, force: true });
}

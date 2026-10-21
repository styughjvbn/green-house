import { existsSync, readFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { parseEnv } from "node:util";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";

const rootEnv = fileURLToPath(new URL("../.env", import.meta.url));

export function developmentEnvironment(
  file = rootEnv,
  inherited = process.env,
) {
  const values = existsSync(file) ? parseEnv(readFileSync(file, "utf8")) : {};
  return { ...values, ...inherited };
}

if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const [command, ...args] = process.argv.slice(2);
  if (!command) throw new Error("A development command is required");
  const child = spawn(command, args, {
    stdio: "inherit",
    env: { ...developmentEnvironment(), GREENHOUSE_DEV_ENV_LOADED: "1" },
  });
  for (const signal of ["SIGINT", "SIGTERM"])
    process.on(signal, () => child.kill(signal));
  child.on("error", () => {
    console.error("Unable to start development command");
    process.exitCode = 1;
  });
  child.on("exit", (code, signal) => {
    process.exitCode = code ?? (signal === "SIGINT" ? 130 : 143);
  });
}

import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { developmentEnvironment } from "../../scripts/dev-environment.mjs";

process.chdir(fileURLToPath(new URL("..", import.meta.url)));
Object.assign(process.env, developmentEnvironment());
process.env.NODE_ENV = "development";
const compiler = fileURLToPath(
  new URL("../node_modules/typescript/bin/tsc", import.meta.url),
);
const children = new Set();
let stopping = false;
function stop(code = 0) {
  if (stopping) return;
  stopping = true;
  process.exitCode = code;
  for (const child of children) child.kill("SIGTERM");
}
for (const signal of ["SIGINT", "SIGTERM"]) process.on(signal, () => stop());
function run(args) {
  const child = spawn(process.execPath, args, { stdio: "inherit" });
  children.add(child);
  child.on("error", () => stop(1));
  child.on("exit", () => children.delete(child));
  return child;
}
const build = run([compiler]);
build.on("exit", (code) => {
  if (stopping) return;
  if (code !== 0) return stop(code ?? 1);
  const watch = run([compiler, "--watch", "--preserveWatchOutput"]);
  const server = run(["--watch", "dist/server.js"]);
  for (const child of [watch, server])
    child.on("exit", (code) => stop(code ?? 1));
});

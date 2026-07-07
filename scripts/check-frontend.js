// scripts/check-frontend.js
const { spawnSync } = require("child_process");

const commands = [
  ["npm", ["run", "format"]],
  ["npm", ["run", "lint"]],
  ["npm", ["run", "build"]],
];

for (const [cmd, args] of commands) {
  const result = spawnSync(cmd, args, {
    encoding: "utf-8",
    shell: process.platform === "win32",
  });

  if (result.status !== 0) {
    console.error(`\n[FAILED] ${cmd} ${args.join(" ")}`);

    if (result.stdout?.trim()) {
      console.error("\n--- stdout ---");
      console.error(result.stdout.trim());
    }

    if (result.stderr?.trim()) {
      console.error("\n--- stderr ---");
      console.error(result.stderr.trim());
    }

    process.exit(result.status ?? 1);
  }
}

console.log("frontend check passed");
export type AppEnvironment = "dev" | "prod";

export function resolveAppEnvironment(
  configuredEnvironment: string | undefined,
  nodeEnvironment: string | undefined,
): AppEnvironment {
  const configured = configuredEnvironment?.trim().toLowerCase();
  if (configured === "dev" || configured === "prod") return configured;
  if (configured) {
    throw new Error(`지원하지 않는 APP_ENV입니다: ${configuredEnvironment}`);
  }
  return nodeEnvironment === "development" ? "dev" : "prod";
}

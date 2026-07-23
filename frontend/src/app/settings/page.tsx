import { SettingsPage } from "@/features/settings";

export default function Page() {
  return <SettingsPage demoMode={process.env.DEMO_MODE === "true"} />;
}

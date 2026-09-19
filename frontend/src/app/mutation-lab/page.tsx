import { MutationLabPage } from "@/features/mutation-lab";
import { resolveAppEnvironment } from "@/shared/config/appEnvironment";
import { notFound } from "next/navigation";

export default function Page() {
  if (
    resolveAppEnvironment(process.env.APP_ENV, process.env.NODE_ENV) !== "dev"
  ) {
    notFound();
  }
  return <MutationLabPage />;
}

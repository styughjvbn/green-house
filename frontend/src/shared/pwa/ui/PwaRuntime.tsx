import { PwaViewportHeightFix } from "./PwaViewportHeightFix";
import { ServiceWorkerRegister } from "./ServiceWorkerRegister";

export function PwaRuntime() {
  return (
    <>
      <PwaViewportHeightFix />
      <ServiceWorkerRegister />
    </>
  );
}

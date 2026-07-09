import { Suspense } from "react";
import { LoginPage } from "@/features/auth/ui/LoginPage";

export default function Page() {
  return (
    <Suspense>
      <LoginPage />
    </Suspense>
  );
}

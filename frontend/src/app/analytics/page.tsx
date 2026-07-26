import { redirect } from "next/navigation";
import { ANALYTICS_ROUTE, DEFAULT_ANALYTICS_TAB } from "@/shared/config/routes";

export default function Page() {
  redirect(ANALYTICS_ROUTE.tab(DEFAULT_ANALYTICS_TAB));
}

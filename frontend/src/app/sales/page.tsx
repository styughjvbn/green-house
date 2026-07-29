import { redirect } from "next/navigation";
import { DEFAULT_SALES_TAB, SALES_ROUTE } from "@/shared/config/routes";

export default function Page() {
  redirect(SALES_ROUTE.tab(DEFAULT_SALES_TAB));
}

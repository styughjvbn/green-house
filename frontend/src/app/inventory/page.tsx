import { redirect } from "next/navigation";
import { DEFAULT_INVENTORY_TAB, INVENTORY_ROUTE } from "@/shared/config/routes";

export default function Page() {
  redirect(INVENTORY_ROUTE.tab(DEFAULT_INVENTORY_TAB));
}

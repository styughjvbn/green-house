import { redirect } from "next/navigation";
import { SALES_V2_ROUTE } from "@/shared/config/routes";
export default function Page() {
  redirect(SALES_V2_ROUTE.tab("slips"));
}

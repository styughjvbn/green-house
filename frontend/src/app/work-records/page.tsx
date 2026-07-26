import { redirect } from "next/navigation";
import {
  DEFAULT_WORK_RECORD_TAB,
  WORK_RECORD_ROUTE,
} from "@/shared/config/routes";

export default function Page() {
  redirect(WORK_RECORD_ROUTE.tab(DEFAULT_WORK_RECORD_TAB));
}

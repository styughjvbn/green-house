import type { ReactNode } from "react";
import { OrchidClipboardProvider } from "@/features/orchid-management/model/OrchidClipboardContext";

export default function OrchidGroupsLayout({
  children,
}: {
  children: ReactNode;
}) {
  return <OrchidClipboardProvider>{children}</OrchidClipboardProvider>;
}

import type { ReactNode } from "react";
import { OrchidClipboardProvider } from "@/features/orchid-management";

export default function OrchidGroupsLayout({
  children,
}: {
  children: ReactNode;
}) {
  return <OrchidClipboardProvider>{children}</OrchidClipboardProvider>;
}

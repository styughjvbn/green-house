import type { ComponentProps } from "react";
import { Plus } from "lucide-react";
import { cn } from "@/shared/lib/cn";

export function DataTableCreateButton({
  children,
  className,
  ...props
}: Omit<ComponentProps<"button">, "type">) {
  return (
    <button
      {...props}
      className={cn(
        "inline-flex h-8 items-center gap-2 rounded-md bg-[#159447] px-3 text-xs font-semibold whitespace-nowrap text-white shadow-sm",
        className,
      )}
      type="button"
    >
      <Plus className="h-4 w-4" strokeWidth={1.8} aria-hidden="true" />
      {children}
    </button>
  );
}

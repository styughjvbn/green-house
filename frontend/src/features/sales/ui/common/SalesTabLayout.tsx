import type { ReactNode } from "react";

const defaultColumns = "lg:grid-cols-[minmax(0,0.88fr)_minmax(0,1.12fr)]";

export function SalesTabLayout({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex h-full min-h-0 flex-col gap-4 ${className}`}>
      {children}
    </div>
  );
}

export function SalesTabStack({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={`flex h-full min-h-0 flex-col gap-3 ${className}`}>
      {children}
    </div>
  );
}

export function SalesTabSplit({
  children,
  columns = defaultColumns,
  gap = "gap-4",
}: {
  children: ReactNode;
  columns?: string;
  gap?: string;
}) {
  return (
    <div className={`grid min-h-0 min-w-0 flex-1 ${gap} ${columns}`}>
      {children}
    </div>
  );
}

export function SalesTabError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }

  return (
    <p className="rounded-md border border-[#f0c7c3] bg-[#fff1ef] px-3 py-2 text-sm font-semibold text-[#b83e35]">
      {message}
    </p>
  );
}

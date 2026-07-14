"use client";

import Link from "next/link";
import { MoreHorizontal } from "lucide-react";
import {
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type ReactNode,
} from "react";
import type { LucideProps } from "lucide-react";

export function SalesDetailCard({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section
      className={`h-full min-h-0 min-w-0 overflow-y-auto rounded-md border border-[#dfe5dc] bg-white shadow-sm ${className}`}
    >
      {children}
    </section>
  );
}

export function SalesDetailHeader({
  actions,
  eyebrow,
  eyebrowAside,
  summary,
  title,
}: {
  actions?: ReactNode;
  eyebrow?: ReactNode;
  eyebrowAside?: ReactNode;
  summary?: ReactNode;
  title: ReactNode;
}) {
  return (
    <header className="border-b border-[#e7ebe5]">
      <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
        <div className="min-w-0">
          {eyebrow || eyebrowAside ? (
            <div className="flex flex-wrap items-center gap-2">
              {eyebrow ? (
                <p className="text-xs font-semibold text-[#6b786f]">
                  {eyebrow}
                </p>
              ) : null}
              {eyebrowAside}
            </div>
          ) : null}
          <h2 className="truncate text-base font-bold text-[#17251b]">
            {title}
          </h2>
        </div>
        {actions ? (
          <SalesDetailHeaderActions>{actions}</SalesDetailHeaderActions>
        ) : null}
      </div>
      {summary ? (
        <div className="border-t border-[#edf0ec]">{summary}</div>
      ) : null}
    </header>
  );
}

export function SalesDetailEmpty({ children }: { children: ReactNode }) {
  return (
    <section className="flex h-full min-h-0 items-center justify-center rounded-md border border-[#dfe5dc] bg-white p-8 text-center text-sm text-[#6c786f]">
      {children}
    </section>
  );
}

type SalesDetailSummaryItem = {
  label: string;
  value: ReactNode;
};

export function SalesDetailSummary({
  align = "right",
  columns = "sm:grid-cols-4",
  items,
}: {
  align?: "left" | "center" | "right";
  columns?: string;
  items: SalesDetailSummaryItem[];
}) {
  return (
    <div className={`grid grid-cols-2 gap-px bg-[#e5e9e3] ${columns}`}>
      {items.map((item) => (
        <div
          key={item.label}
          className={`bg-[#fbfcfa] px-3 py-3 ${summaryAlignClass(align)}`}
        >
          <p className="text-[11px] font-semibold text-[#68756c]">
            {item.label}
          </p>
          <p className="mt-1 text-sm font-bold text-[#17251b]">{item.value}</p>
        </div>
      ))}
    </div>
  );
}

type DetailActionTone = "default" | "primary" | "danger";

type SalesDetailActionButtonProps = {
  children: ReactNode;
  disabled?: boolean;
  href?: string;
  icon?: ComponentType<LucideProps>;
  tone?: DetailActionTone;
  onClick?: () => void;
};

export function SalesDetailActionButton({
  children,
  disabled = false,
  href,
  icon: Icon,
  tone = "default",
  onClick,
}: SalesDetailActionButtonProps) {
  const className = [
    "inline-flex h-8 items-center gap-1.5 rounded-md px-3 text-xs font-semibold disabled:opacity-60",
    actionToneClass(tone),
  ].join(" ");
  const content = (
    <>
      {Icon ? (
        <Icon className="h-3.5 w-3.5" strokeWidth={1.8} aria-hidden="true" />
      ) : null}
      {children}
    </>
  );

  if (href) {
    return (
      <Link className={className} href={href}>
        {content}
      </Link>
    );
  }

  return (
    <button
      className={className}
      type="button"
      disabled={disabled}
      onClick={onClick}
    >
      {content}
    </button>
  );
}

function actionToneClass(tone: DetailActionTone) {
  if (tone === "primary") {
    return "bg-[#159447] text-white";
  }
  if (tone === "danger") {
    return "border border-[#f0cfc7] bg-white text-[#a64835]";
  }
  return "border border-[#d7ded5] bg-white text-[#344138]";
}

function summaryAlignClass(align: "left" | "center" | "right") {
  if (align === "center") {
    return "text-center";
  }
  if (align === "right") {
    return "text-right";
  }
  return "text-left";
}

function SalesDetailHeaderActions({ children }: { children: ReactNode }) {
  const rootRef = useRef<HTMLDivElement | null>(null);
  const measureRef = useRef<HTMLDivElement | null>(null);

  const [collapsed, setCollapsed] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    function syncCollapsed() {
      const measure = measureRef.current;
      if (!measure) return;

      const nextCollapsed = measure.scrollHeight > 40;

      setCollapsed((current) =>
        current === nextCollapsed ? current : nextCollapsed,
      );
      if (!nextCollapsed) {
        setOpen(false);
      }
    }

    syncCollapsed();

    const observer = new ResizeObserver(syncCollapsed);

    if (rootRef.current) {
      observer.observe(rootRef.current);
    }

    if (measureRef.current) {
      observer.observe(measureRef.current);
    }

    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!open) return;

    function handlePointerDown(event: PointerEvent) {
      const target = event.target;

      if (
        target instanceof Node &&
        rootRef.current &&
        !rootRef.current.contains(target)
      ) {
        setOpen(false);
      }
    }

    document.addEventListener("pointerdown", handlePointerDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative flex min-w-0 flex-1 justify-end">
      <div
        ref={measureRef}
        className="pointer-events-none invisible absolute top-0 right-0 flex w-full flex-wrap items-center justify-end gap-2"
        aria-hidden="true"
      >
        {children}
      </div>

      {collapsed ? (
        <div
          className="relative"
          onBlur={(event) => {
            if (!event.currentTarget.contains(event.relatedTarget)) {
              setOpen(false);
            }
          }}
        >
          <button
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-[#d7ded5] bg-white text-[#344138] hover:bg-[#f6f8f5]"
            type="button"
            aria-label="상세 작업 더보기"
            title="상세 작업 더보기"
            aria-expanded={open}
            onClick={() => setOpen((current) => !current)}
          >
            <MoreHorizontal
              className="h-3.5 w-3.5"
              strokeWidth={1.8}
              aria-hidden="true"
            />
          </button>

          {open ? (
            <div className="absolute top-9 right-0 z-30 flex w-48 flex-col gap-1 rounded-md border border-[#dfe5dc] bg-white p-2 shadow-xl [&>*]:w-full [&>*]:justify-start">
              {children}
            </div>
          ) : null}
        </div>
      ) : (
        <div className="flex items-center justify-end gap-2">{children}</div>
      )}
    </div>
  );
}
